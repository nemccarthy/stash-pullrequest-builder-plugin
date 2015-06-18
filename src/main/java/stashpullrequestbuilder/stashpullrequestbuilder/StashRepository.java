package stashpullrequestbuilder.stashpullrequestbuilder;

import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashApiClient;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestComment;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestMergableResponse;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestResponseValue;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestResponseValueRepository;

import hudson.EnvVars;
import hudson.model.Environment;
import hudson.model.Hudson;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.PreBuildMerge;
import hudson.security.ACL;
import hudson.slaves.NodeProperty;

import java.io.IOException;
import java.net.URISyntaxException;
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

import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.lang.StringUtils;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.protocol.HTTP;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.GitClient;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;

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

    public static final String ADDITIONAL_PARAMETER_REGEX = "^p:(([A-Za-z_0-9])+)=(.*)";
    public static final Pattern ADDITIONAL_PARAMETER_REGEX_PATTERN = Pattern.compile(ADDITIONAL_PARAMETER_REGEX);

    private StashPullRequestsBuilder builder;
    private String targetBranchFilter;
    private StashBuildTrigger trigger;
    private StashApiClient client;

    public StashRepository(StashPullRequestsBuilder builder) {
        this.builder = builder;
    }

    public void init() {
        trigger = this.builder.getTrigger();
        
        URIish uri = null;
        StandardUsernamePasswordCredentials credentials = null;
        if (StringUtils.isNotBlank(trigger.getTargetBranchFilter()))
        	targetBranchFilter = trigger.getTargetBranchFilter(); 
        
        if (this.builder.getProject().getScm() instanceof GitSCM) {
        	GitSCM scm = (GitSCM) this.builder.getProject().getScm();
        	
        	// retrieve environment variables (so they can be used in hostname)
        	EnvVars env = new EnvVars(System.getenv());
    		for (NodeProperty<?> nodeProperty: Hudson.getInstance().getGlobalNodeProperties()) {
				try {
					Environment environment = nodeProperty.setUp(null, null, null);
	                if (environment != null) {
	                    environment.buildEnvVars(env);
	                }
				} catch (IOException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
            }

    		// resolve environment variables against each other
            EnvVars.resolve(env);
        	
            // TODO: add support for multiple repositories
            if (scm.getUserRemoteConfigs().size() > 0) {
            	UserRemoteConfig config = scm.getUserRemoteConfigs().get(0);
            	try {
	            	// retrieve host from SCM config && expand using previously searched environment variables
	            	uri = new URIish(env.expand(config.getUrl()));
				} catch (URISyntaxException e) {
					throw new IllegalStateException("invalid stash uri in SCM configuration", e);
				}
            	
            	// get target branch if option is selected and isn't overridden by trigger configuration
            	if (trigger.isCheckMergeBeforeBuild() && StringUtils.isBlank(trigger.getTargetBranchFilter())) {
	        		for (GitSCMExtension extension : scm.getExtensions().toList()) {
	        			if (extension instanceof PreBuildMerge) {
	        				PreBuildMerge preBuildMerge = (PreBuildMerge) extension;
	        				targetBranchFilter = env.expand(preBuildMerge.getOptions().getMergeTarget());
	        				break;
	        			}
	        		}
            	}
            	
            	if ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) {
	            	// get credentials from credentials provider if url is HTTP/HTTPS
            		// if not, it's no use to get them since they will be different for HTTP/HTTPS
            		// which is required for REST calls to Stash
	                if (config.getCredentialsId() != null) {
	                	credentials = CredentialsMatchers.firstOrNull(
	                    		CredentialsProvider.lookupCredentials(
	                    				StandardUsernamePasswordCredentials.class, this.builder.getProject(),
	                    				ACL.SYSTEM, URIRequirementBuilder.fromUri(uri.toString()).build()
	                    		), CredentialsMatchers.allOf(
	                    				CredentialsMatchers.withId(config.getCredentialsId()), GitClient.CREDENTIALS_MATCHER
	                    		)
	                    );
	                }
	                
	                client = new StashApiClient(uri, credentials);
            	} else {
            		// retrieve host from trigger && expand using previously searched environment variables
            		client = new StashApiClient(
            				env.expand(trigger.getStashHost()),
            				uri, 
            				new UsernamePasswordCredentials(
            						env.expand(trigger.getStashUsername()), 
            						env.expand(trigger.getStashPassword())
            				)
            			);
            	}
            } else {
            	throw new IllegalStateException("No remote configuration provided for Git SCM");
            }
        }
        
        if (client == null)
        	throw new IllegalStateException("Stash API client wasn't initialized");
    }

    public Collection<StashPullRequestResponseValue> getTargetPullRequests() {
        logger.info("Fetching pull requests ...");
        List<StashPullRequestResponseValue> pullRequests = client.getPullRequests();
        logger.info("Found " + pullRequests.size() + " pull requests");
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
            String commentId = postBuildStartCommentTo(pullRequest);
            StashCause cause = new StashCause(
                    client.getHost(),
                    pullRequest.getFromRef().getBranch().getName(),
                    pullRequest.getToRef().getBranch().getName(),
                    "*/pr/" + pullRequest.getId() + "/from",
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

    public void postFinishedComment(String pullRequestId, String sourceCommit,  String destinationCommit, boolean success, String buildUrl, int buildNumber, String additionalComment, String duration) {
        String message = BUILD_FAILURE_COMMENT;
        if (success){
            message = BUILD_SUCCESS_COMMENT;
        }
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

    private boolean isBuildTarget(StashPullRequestResponseValue pullRequest) {

        boolean shouldBuild = true;
        logger.fine("Verifying " + pullRequest);
        if (pullRequest.getState() != null && pullRequest.getState().equals("OPEN")) {
            if (isSkipBuild(pullRequest.getTitle())) {
                return false;
            }
            
            if(!isAllowedTargetBranch(pullRequest.getToRef().getBranch().getName())) {
            	logger.info("skipping trigger, " + pullRequest.getToRef().getBranch().getName() + 
            			" doesn't match " + targetBranchFilter);
            	return false;
            }
            
            if(!isPullRequestMergable(pullRequest)) {
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

                    if (isPhrasesContain(content, this.trigger.getCiBuildPhrases())) {
                        shouldBuild = true;
                        break;
                    }
                }
            }
        }
        return shouldBuild;
    }

    private boolean isSkipBuild(String pullRequestTitle) {
        String skipPhrases = this.trigger.getCiSkipPhrases();
        if (skipPhrases != null && !"".equals(skipPhrases)) {
            String[] phrases = skipPhrases.split(",");
            for(String phrase : phrases) {
                if (isPhrasesContain(pullRequestTitle, phrase)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isAllowedTargetBranch(String pullRequestToBranch) {
        if (StringUtils.isNotBlank(targetBranchFilter)) {
        	return pullRequestToBranch.matches(targetBranchFilter);
        }
        return true;
    }

    private boolean isPhrasesContain(String text, String phrase) {
        return text != null && text.toLowerCase().contains(phrase.trim().toLowerCase());
    }
}
