package stashpullrequestbuilder.stashpullrequestbuilder;

import hudson.model.Result;

import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashApiClient;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestComment;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestMergableResponse;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestResponseValue;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestResponseValueRepository;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Nathan McCarthy
 */
public class StashRepository {
    private static final Logger logger = Logger.getLogger(StashRepository.class.getName());
    public static final String BUILD_START_MARKER = "[*BuildStarted* **%s**] %s into %s";
    public static final String BUILD_FINISH_MARKER = "[*BuildFinished* **%s**] %s into %s";

    public static final String BUILD_START_REGEX = "\\[\\*BuildStarted\\* \\*\\*%s\\*\\*\\] ([0-9a-fA-F]+) into ([0-9a-fA-F]+)";
    public static final String BUILD_FINISH_REGEX = "\\[\\*BuildFinished\\* \\*\\*%s\\*\\*\\] ([0-9a-fA-F]+) into ([0-9a-fA-F]+)";

    public static final String BUILD_FINISH_SENTENCE = BUILD_FINISH_MARKER + " \n\n **[%s](%s)** - Build *#%d* which took *%s*";
    public static final String BUILD_START_SENTENCE = BUILD_START_MARKER + " \n\n **[%s](%s)** - Build *#%d*";

    public static final String BUILD_SUCCESS_COMMENT =  "✓ BUILD SUCCESS";
    public static final String BUILD_FAILURE_COMMENT = "✕ BUILD FAILURE";
    public static final String BUILD_RUNNING_COMMENT = "BUILD RUNNING...";
    public static final String BUILD_UNSTABLE_COMMENT = "⁉ BUILD UNSTABLE";
    public static final String BUILD_ABORTED_COMMENT = "‼ BUILD ABORTED";
    public static final String BUILD_NOTBUILT_COMMENT = "✕ BUILD INCOMPLETE";

    public static final String ADDITIONAL_PARAMETER_REGEX = "^p:(([A-Za-z_0-9])+)=(.*)";
    public static final Pattern ADDITIONAL_PARAMETER_REGEX_PATTERN = Pattern.compile(ADDITIONAL_PARAMETER_REGEX);

    private String projectPath;
    private StashPullRequestsBuilder builder;
    private StashBuildTrigger trigger;
    private StashApiClient client;

    public StashRepository(String projectPath, StashPullRequestsBuilder builder) {
        this.projectPath = projectPath;
        this.builder = builder;
    }

    public void init() {
        trigger = this.builder.getTrigger();
        client = new StashApiClient(
                trigger.getStashHost(),
                trigger.getUsername(),
                trigger.getPassword(),
                trigger.getProjectCode(),
                trigger.getRepositoryName(),
                trigger.isIgnoreSsl());
    }

    public Collection<StashPullRequestResponseValue> getTargetPullRequests() {
        logger.info("Fetch PullRequests.");
        List<StashPullRequestResponseValue> pullRequests = client.getPullRequests();
        List<StashPullRequestResponseValue> targetPullRequests = new ArrayList<StashPullRequestResponseValue>();
        for(StashPullRequestResponseValue pullRequest : pullRequests) {
            if (isBuildTarget(pullRequest)) {
                targetPullRequests.add(pullRequest);
            }
        }
        return targetPullRequests;
    }

    public String postBuildStartCommentTo(StashPullRequestResponseValue pullRequest) {
            String sourceCommit = pullRequest.getFromRef().getLatestCommit();
            String destinationCommit = pullRequest.getToRef().getLatestCommit();
            String comment = String.format(BUILD_START_MARKER, builder.getProject().getDisplayName(), sourceCommit, destinationCommit);
            StashPullRequestComment commentResponse = this.client.postPullRequestComment(pullRequest.getId(), comment);
            return commentResponse.getCommentId().toString();
    }

    public static AbstractMap.SimpleEntry<String,String> getParameter(String content){
    	if(content.isEmpty()){
    		return null;
    	}
        Matcher parameterMatcher = ADDITIONAL_PARAMETER_REGEX_PATTERN.matcher(content);
        if(parameterMatcher.find(0)){
        	String parameterName = parameterMatcher.group(1);
        	String parameterValue = parameterMatcher.group(3);
        	return new AbstractMap.SimpleEntry<String,String>(parameterName, parameterValue);
        }
        return null;
    }
    
    public static Map<String, String> getParametersFromContent(String content){
        Map<String, String> result = new TreeMap<String, String>();
		String lines[] = content.split("\\r?\\n|\\r");
		for(String line : lines){
			AbstractMap.SimpleEntry<String,String> parameter = getParameter(line);
			if(parameter != null){
				result.put(parameter.getKey(), parameter.getValue());
			}
		}
        
        return result;
   }
    
    public Map<String, String> getAdditionalParameters(StashPullRequestResponseValue pullRequest){
        StashPullRequestResponseValueRepository destination = pullRequest.getToRef();
        String owner = destination.getRepository().getProjectName();
        String repositoryName = destination.getRepository().getRepositoryName();

        String id = pullRequest.getId();
        List<StashPullRequestComment> comments = client.getPullRequestComments(owner, repositoryName, id);
        if (comments != null) {
            Collections.sort(comments);
//          Collections.reverse(comments);

            Map<String, String> result = new TreeMap<String, String>();
            
            for (StashPullRequestComment comment : comments) {
                String content = comment.getText();
                if (content == null || content.isEmpty()) {
                    continue;
                }

                Map<String,String> parameters = getParametersFromContent(content);
                for(String key : parameters.keySet()){
                	result.put(key, parameters.get(key));
                }
            }
            return result;
        }
        return null;
    }
    
    public void addFutureBuildTasks(Collection<StashPullRequestResponseValue> pullRequests) {
        for(StashPullRequestResponseValue pullRequest : pullRequests) {
        	Map<String, String> additionalParameters = getAdditionalParameters(pullRequest);
                if (trigger.getDeletePreviousBuildFinishComments()) {
                    deletePreviousBuildFinishedComments(pullRequest);
                }
            String commentId = postBuildStartCommentTo(pullRequest);
            StashCause cause = new StashCause(
                    trigger.getStashHost(),
                    pullRequest.getFromRef().getBranch().getName(),
                    pullRequest.getToRef().getBranch().getName(),
                    pullRequest.getFromRef().getRepository().getProjectName(),
                    pullRequest.getFromRef().getRepository().getRepositoryName(),
                    pullRequest.getId(),
                    pullRequest.getToRef().getRepository().getProjectName(),
                    pullRequest.getToRef().getRepository().getRepositoryName(),
                    pullRequest.getTitle(),
                    pullRequest.getFromRef().getLatestCommit(),
                    pullRequest.getToRef().getLatestCommit(),
                    commentId,
                    additionalParameters);
            this.builder.getTrigger().startJob(cause);

        }
    }

    public void deletePullRequestComment(String pullRequestId, String commentId) {
        this.client.deletePullRequestComment(pullRequestId, commentId);
    }

    private String getMessageForBuildResult(Result result) {
        String message = BUILD_FAILURE_COMMENT;
        if (result == Result.SUCCESS) {
            message = BUILD_SUCCESS_COMMENT;
        }
        if (result == Result.UNSTABLE) {
            message = BUILD_UNSTABLE_COMMENT;
        }
        if (result == Result.ABORTED) {
            message = BUILD_ABORTED_COMMENT;
        }
        if (result == Result.NOT_BUILT) {
            message = BUILD_NOTBUILT_COMMENT;
        }
        return message;
    }
    
    public void postFinishedComment(String pullRequestId, String sourceCommit,  String destinationCommit, Result buildResult, String buildUrl, int buildNumber, String additionalComment, String duration) {
        String message = getMessageForBuildResult(buildResult);
        String comment = String.format(BUILD_FINISH_SENTENCE, builder.getProject().getDisplayName(), sourceCommit, destinationCommit, message, buildUrl, buildNumber, duration);

        comment = comment.concat(additionalComment);

        this.client.postPullRequestComment(pullRequestId, comment);
    }

    private Boolean isPullRequestMergable(StashPullRequestResponseValue pullRequest) {
        if (trigger.isCheckMergeable() || trigger.isCheckNotConflicted()) {
            StashPullRequestMergableResponse mergable = client.getPullRequestMergeStatus(pullRequest.getId());
            if (trigger.isCheckMergeable())
                return  mergable.getCanMerge();
            if (trigger.isCheckNotConflicted())
                return !mergable.getConflicted();
        }
        return true;
    }

    private void deletePreviousBuildFinishedComments(StashPullRequestResponseValue pullRequest) {

        StashPullRequestResponseValueRepository destination = pullRequest.getToRef();
        String owner = destination.getRepository().getProjectName();
        String repositoryName = destination.getRepository().getRepositoryName();
        String id = pullRequest.getId();

        List<StashPullRequestComment> comments = client.getPullRequestComments(owner, repositoryName, id);

        if (comments != null) {
            Collections.sort(comments);
                Collections.reverse(comments);
                for (StashPullRequestComment comment : comments) {
                    String content = comment.getText();
                    if (content == null || content.isEmpty()) {
                        continue;
                    }

                    String project_build_finished = String.format(BUILD_FINISH_REGEX, builder.getProject().getDisplayName());
                    Matcher finishMatcher = Pattern.compile(project_build_finished, Pattern.CASE_INSENSITIVE).matcher(content);

                    if (finishMatcher.find()) {
                        deletePullRequestComment(pullRequest.getId(), comment.getCommentId().toString());
                    }
                }
        }
    }

    private boolean isBuildTarget(StashPullRequestResponseValue pullRequest) {

        boolean shouldBuild = true;

        if (pullRequest.getState() != null && pullRequest.getState().equals("OPEN")) {
            if (isSkipBuild(pullRequest.getTitle())) {
                logger.info("Skipping PR: " + pullRequest.getId() + " as title contained skip phrase");
                return false;
            }

            if (!isForTargetBranch(pullRequest)) {
                logger.info("Skipping PR: " + pullRequest.getId() + " as targeting branch: " + pullRequest.getToRef().getBranch().getName());
                return false;
            }

            if(!isPullRequestMergable(pullRequest)) {
                logger.info("Skipping PR: " + pullRequest.getId() + " as cannot be merged");
                return false;
            }

            if (trigger.isOnlyBuildOnComment()) {
                shouldBuild = false;
            }

            String sourceCommit = pullRequest.getFromRef().getLatestCommit();

            StashPullRequestResponseValueRepository destination = pullRequest.getToRef();
            String owner = destination.getRepository().getProjectName();
            String repositoryName = destination.getRepository().getRepositoryName();
            String destinationCommit = destination.getLatestCommit();

            String id = pullRequest.getId();
            List<StashPullRequestComment> comments = client.getPullRequestComments(owner, repositoryName, id);

            if (comments != null) {
                Collections.sort(comments);
                Collections.reverse(comments);
                for (StashPullRequestComment comment : comments) {
                    String content = comment.getText();
                    if (content == null || content.isEmpty()) {
                        continue;
                    }

                    //These will match any start or finish message -- need to check commits
                    String project_build_start = String.format(BUILD_START_REGEX, builder.getProject().getDisplayName());
                    String project_build_finished = String.format(BUILD_FINISH_REGEX, builder.getProject().getDisplayName());
                    Matcher startMatcher = Pattern.compile(project_build_start, Pattern.CASE_INSENSITIVE).matcher(content);
                    Matcher finishMatcher = Pattern.compile(project_build_finished, Pattern.CASE_INSENSITIVE).matcher(content);

                    if (startMatcher.find() ||
                        finishMatcher.find()) {

                        String sourceCommitMatch;
                        String destinationCommitMatch;

                        if (startMatcher.find(0)) {
                            sourceCommitMatch = startMatcher.group(1);
                            destinationCommitMatch = startMatcher.group(2);
                        } else {
                            sourceCommitMatch = finishMatcher.group(1);
                            destinationCommitMatch = finishMatcher.group(2);
                        }

                        //first check source commit -- if it doesn't match, just move on. If it does, investigate further.
                        if (sourceCommitMatch.equalsIgnoreCase(sourceCommit)) {
                            // if we're checking destination commits, and if this doesn't match, then move on.
                            if (this.trigger.getCheckDestinationCommit()
                                    && (!destinationCommitMatch.equalsIgnoreCase(destinationCommit))) {
                            	continue;
                            }

                            shouldBuild = false;
                            break;
                        }
                    }

                    if(isSkipBuild(content)) {
                        shouldBuild = false;
                        break;
                    } if (isPhrasesContain(content, this.trigger.getCiBuildPhrases())) {
                        shouldBuild = true;
                        break;
                    }
                }
            }
        }
        return shouldBuild;
    }

    private boolean isForTargetBranch(StashPullRequestResponseValue pullRequest) {
        String targetBranchesToBuild = this.trigger.getTargetBranchesToBuild();
        if (targetBranchesToBuild !=null && !"".equals(targetBranchesToBuild)) {
            String[] branches = targetBranchesToBuild.split(",");
            for(String branch : branches) {
                if (pullRequest.getToRef().getBranch().getName().matches(branch.trim())) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private boolean isSkipBuild(String pullRequestContentString) {
        String skipPhrases = this.trigger.getCiSkipPhrases();
        if (skipPhrases != null && !"".equals(skipPhrases)) {
            String[] phrases = skipPhrases.split(",");
            for(String phrase : phrases) {
                if (isPhrasesContain(pullRequestContentString, phrase)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isPhrasesContain(String text, String phrase) {
        return text != null && text.toLowerCase().contains(phrase.trim().toLowerCase());
    }
}
