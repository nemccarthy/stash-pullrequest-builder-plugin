package stashpullrequestbuilder.stashpullrequestbuilder;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Cause;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import jenkins.model.Jenkins;
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

import static java.lang.String.format;

/**
 * Created by Nathan McCarthy
 */
@SuppressFBWarnings("WMI_WRONG_MAP_ITERATOR")
public class StashRepository {
    private static final Logger logger = Logger.getLogger(StashRepository.class.getName());
    public static final String BUILD_START_MARKER = "[*BuildStarted* **%s**] %s into %s";
    public static final String BUILD_FINISH_MARKER = "[*BuildFinished* **%s**] %s into %s";

    public static final String BUILD_START_REGEX = "\\[\\*BuildStarted\\* \\*\\*%s\\*\\*\\] ([0-9a-fA-F]+) into ([0-9a-fA-F]+)";
    public static final String BUILD_FINISH_REGEX = "\\[\\*BuildFinished\\* \\*\\*%s\\*\\*\\] ([0-9a-fA-F]+) into ([0-9a-fA-F]+)";

    public static final String BUILD_FINISH_SENTENCE = BUILD_FINISH_MARKER + " %n%n **[%s](%s)** - Build *#%d* which took *%s*";
    public static final String BUILD_START_SENTENCE = BUILD_START_MARKER + " %n%n **[%s](%s)** - Build *#%d*";

    public static final String BUILD_SUCCESS_COMMENT = "✓ BUILD SUCCESS";
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

    public Collection<StashPullRequestResponseValue> getBuildablePullRequests() {
        logger.finer(format("%s: Fetch pull requests.", builder.getJob().getName()));
        List<StashPullRequestResponseValue> pullRequests = client.getPullRequests();
        List<StashPullRequestResponseValue> buildablePullRequests = new ArrayList<StashPullRequestResponseValue>();
        for (StashPullRequestResponseValue pullRequest : pullRequests) {
            if (isBuildable(pullRequest)) {
                buildablePullRequests.add(pullRequest);
            }
        }
        return buildablePullRequests;
    }

    public String postBuildStartCommentTo(StashPullRequestResponseValue pullRequest) {
        String sourceCommit = pullRequest.getFromRef().getLatestCommit();
        String destinationCommit = pullRequest.getToRef().getLatestCommit();
        String comment = format(BUILD_START_MARKER, builder.getJob().getDisplayName(), sourceCommit, destinationCommit);
        StashPullRequestComment commentResponse = this.client.postPullRequestComment(pullRequest.getId(), comment);
        return commentResponse.getCommentId().toString();
    }

    public static AbstractMap.SimpleEntry<String, String> getParameter(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        Matcher parameterMatcher = ADDITIONAL_PARAMETER_REGEX_PATTERN.matcher(content);
        if (parameterMatcher.find(0)) {
            String parameterName = parameterMatcher.group(1);
            String parameterValue = parameterMatcher.group(3);
            return new AbstractMap.SimpleEntry<String, String>(parameterName, parameterValue);
        }
        return null;
    }

    public static Map<String, String> getParametersFromContent(String content) {
        Map<String, String> result = new TreeMap<String, String>();
        String lines[] = content.split("\\r?\\n|\\r");
        for (String line : lines) {
            AbstractMap.SimpleEntry<String, String> parameter = getParameter(line);
            if (parameter != null) {
                result.put(parameter.getKey(), parameter.getValue());
            }
        }

        return result;
    }

    public Map<String, String> getAdditionalParameters(StashPullRequestResponseValue pullRequest) {
        StashPullRequestResponseValueRepository destination = pullRequest.getToRef();
        String repositoryName = destination.getRepository().getRepositoryName();
        String owner = destination.getRepository().getProjectName();

        String id = pullRequest.getId();
        List<StashPullRequestComment> comments = client.getPullRequestComments(owner, repositoryName, id);
        if (comments != null) {
            Collections.sort(comments);

            Map<String, String> result = new TreeMap<String, String>();
            for (StashPullRequestComment comment : comments) {
                String content = comment.getText();
                if (content == null || content.isEmpty()) {
                    continue;
                }

                Map<String, String> parameters = getParametersFromContent(content);
                for (String key : parameters.keySet()) {
                    result.put(key, parameters.get(key));
                }
            }
            return result;
        }
        return null;
    }

    public void buildPullRequests(Collection<StashPullRequestResponseValue> pullRequests) {
        for (StashPullRequestResponseValue pullRequest : pullRequests) {
            StashPullRequestResponseValueRepository destination = pullRequest.getToRef();
            String pullRequestId = pullRequest.getId();
            String sourceCommitId = pullRequest.getFromRef().getLatestCommit();
            String targetCommitId = pullRequest.getToRef().getLatestCommit();
            String repositoryName = destination.getRepository().getRepositoryName();
            String owner = destination.getRepository().getProjectName();
            long lastTimestamp = pullRequest.getLastActionTimestamp();

            if (lastTimestamp > 0 && isBuiltCause(pullRequestId, sourceCommitId, targetCommitId, lastTimestamp)) {
                continue;
            }

            Map<String, String> additionalParameters = getAdditionalParameters(pullRequest);

            String commentId = null;
            if (!trigger.isDisableBuildComments()) {
                commentId = postBuildStartCommentTo(pullRequest);
            }

            StashCause cause = new StashCause(
                    trigger.getStashHost(),
                    pullRequest.getFromRef().getBranch().getName(),
                    pullRequest.getToRef().getBranch().getName(),
                    pullRequest.getFromRef().getRepository().getProjectName(),
                    pullRequest.getFromRef().getRepository().getRepositoryName(),
                    pullRequestId,
                    owner,
                    repositoryName,
                    pullRequest.getTitle(),
                    sourceCommitId,
                    targetCommitId,
                    commentId,
                    lastTimestamp,
                    pullRequest.getVersion(),
                    additionalParameters);

            if (this.builder.getTrigger().startJob(cause) != null) {
                logger.info(owner + "/" + repositoryName + " #" + pullRequestId + ": Start build pull request");
            }
        }
    }

    private boolean isBuiltCause(String pullRequestId, String sourceCommitId, String targetCommitId, long lastTimestamp) {
        Jenkins jenkins = Jenkins.getInstance();
        Job<?, ?> job = builder.getJob();

        if (jenkins != null) {
            Queue queue = jenkins.getQueue();
            for (Queue.Item item : queue.getItems()) {
                if (item.getDisplayName().equals(job.getDisplayName())) {
                    StashCause sc = getStashCause(item.getCauses(), pullRequestId, sourceCommitId, targetCommitId);
                    if (sc != null) {
                        return true;
                    }
                }
            }

            for (Run<?, ?> run : job.getBuilds()) {
                StashCause sc = getStashCause(run.getCauses(), pullRequestId, sourceCommitId, targetCommitId);
                if (sc != null && sc.getBuildLastTimestamp() >= lastTimestamp) {
                    return true;
                }
            }
        }

        return false;
    }

    private StashCause getStashCause(Collection<Cause> causes, String pullRequestId, String sourceCommitId, String targetCommitId) {
        if (causes != null) {
            for (Cause cause : causes) {
                if (cause instanceof StashCause) {
                    StashCause sc = (StashCause) cause;
                    if (sc.getPullRequestId().equalsIgnoreCase(pullRequestId) &&
                            sc.getSourceCommitHash().equalsIgnoreCase(sourceCommitId) &&
                            sc.getDestinationCommitHash().equalsIgnoreCase(targetCommitId)) {
                        return sc;
                    }
                }
            }
        }
        return null;
    }

    public void deletePullRequestComment(String pullRequestId, String commentId) {
        if (pullRequestId != null && commentId != null) {
            this.client.deletePullRequestComment(pullRequestId, commentId);
        }
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

    public void postFinishedComment(String pullRequestId, String sourceCommit, String destinationCommit, Result buildResult, String buildUrl, int buildNumber, String additionalComment, String duration) {
        String message = getMessageForBuildResult(buildResult);
        String comment = format(BUILD_FINISH_SENTENCE, builder.getJob().getDisplayName(), sourceCommit, destinationCommit, message, buildUrl, buildNumber, duration);

        comment = comment.concat(additionalComment);

        this.client.postPullRequestComment(pullRequestId, comment);
    }

    public boolean mergePullRequest(String pullRequestId, String version) {
        return this.client.mergePullRequest(pullRequestId, version);
    }

    private Boolean isPullRequestMergable(StashPullRequestResponseValue pullRequest) {
        if (trigger.isCheckMergeable() || trigger.isCheckNotConflicted()) {
            StashPullRequestMergableResponse mergable = client.getPullRequestMergeStatus(pullRequest.getId());
            boolean res = true;
            if (trigger.isCheckMergeable())
                res = res && mergable.getCanMerge();
            if (trigger.isCheckNotConflicted())
                res = res && !mergable.getConflicted();
            return res;
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

                String project_build_finished = format(BUILD_FINISH_REGEX, builder.getJob().getDisplayName());
                Matcher finishMatcher = Pattern.compile(project_build_finished, Pattern.CASE_INSENSITIVE).matcher(content);

                if (finishMatcher.find()) {
                    deletePullRequestComment(pullRequest.getId(), comment.getCommentId().toString());
                }
            }
        }
    }

    private boolean isBuildable(StashPullRequestResponseValue pullRequest) {
        if (pullRequest == null || pullRequest.getState() == null || !pullRequest.getState().equals("OPEN")) {
            return false;
        }

        String id = pullRequest.getId();
        StashPullRequestResponseValueRepository destination = pullRequest.getToRef();
        String owner = destination.getRepository().getProjectName();
        String repositoryName = destination.getRepository().getRepositoryName();
        String logPrefix = owner + "/" + repositoryName + " #" + id + ": ";
        boolean isOnlyBuildOnComment = trigger.isOnlyBuildOnComment();
        boolean shouldBuild = !isOnlyBuildOnComment;

        if (isSkipBuild(pullRequest.getTitle())) {
            logger.fine(logPrefix + "Skipping PR as title contained skip phrase");
            return false;
        } else if (!isForTargetBranch(pullRequest)) {
            logger.fine(logPrefix + "Skipping PR as targeting branch: " + pullRequest.getToRef().getBranch().getName());
            return false;
        } else if (!isPullRequestMergable(pullRequest)) {
            logger.fine(logPrefix + "Skipping PR as cannot be merged");
            return false;
        }

        List<StashPullRequestComment> comments = client.getPullRequestComments(owner, repositoryName, id);
        if (comments != null) {
            Collections.sort(comments);
            Collections.reverse(comments);
            for (StashPullRequestComment comment : comments) {
                if (pullRequest.getLastActionTimestamp() == 0) {
                    pullRequest.setLastActionTimestamp(comment.getCreatedDate());
                }

                String content = comment.getText();
                if (content == null || content.isEmpty()) {
                    continue;
                }

                if (isSkipBuild(content)) {
                    return false;
                } else if (isPhrasesContain(content, this.trigger.getCiBuildPhrases())) {
                    return true;
                }
            }
        }

        return shouldBuild;
    }

    private boolean isForTargetBranch(StashPullRequestResponseValue pullRequest) {
        String targetBranchesToBuild = this.trigger.getTargetBranchesToBuild();
        if (targetBranchesToBuild != null && !"".equals(targetBranchesToBuild)) {
            String[] branches = targetBranchesToBuild.split(",");
            for (String branch : branches) {
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
            for (String phrase : phrases) {
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
