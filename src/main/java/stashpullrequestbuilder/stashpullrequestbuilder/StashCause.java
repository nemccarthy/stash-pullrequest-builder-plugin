package stashpullrequestbuilder.stashpullrequestbuilder;

import java.util.Map;

import hudson.model.Cause;

/**
 * Created by Nathan McCarthy
 */
public class StashCause extends Cause {
    private final String sourceBranch;
    private final String targetBranch;
    private final String pullRequestBranch;
    private final String repositoryOwner;
    private final String repositoryName;
    private final String pullRequestId;
    private final String destinationRepositoryOwner;
    private final String destinationRepositoryName;
    private final String pullRequestTitle;
    private final String sourceCommitHash;
    private final String destinationCommitHash;
    private final String buildStartCommentId;
    private final String stashHost;
    private final Map<String,String> additionalParameters;

    public StashCause(String stashHost,
                          String sourceBranch,
                          String targetBranch,
                          String pullRequestBranch,
                          String repositoryOwner,
                          String repositoryName,
                          String pullRequestId,
                          String destinationRepositoryOwner,
                          String destinationRepositoryName,
                          String pullRequestTitle,
                          String sourceCommitHash,
                          String destinationCommitHash,
                          String buildStartCommentId,
                          Map<String,String> additionalParameters) {
        this.sourceBranch = sourceBranch;
        this.targetBranch = targetBranch;
        this.pullRequestBranch = pullRequestBranch;
        this.repositoryOwner = repositoryOwner;
        this.repositoryName = repositoryName;
        this.pullRequestId = pullRequestId;
        this.destinationRepositoryOwner = destinationRepositoryOwner;
        this.destinationRepositoryName = destinationRepositoryName;
        this.pullRequestTitle = pullRequestTitle;
        this.sourceCommitHash = sourceCommitHash;
        this.destinationCommitHash = destinationCommitHash;
        this.buildStartCommentId = buildStartCommentId;
        this.stashHost = stashHost.replaceAll("/$", "");
        this.additionalParameters = additionalParameters;
    }

    public String getSourceBranch() {
        return sourceBranch;
    }
    public String getTargetBranch() {
        return targetBranch;
    }

    public String getPullRequestBranch() {
		return pullRequestBranch;
	}

	public String getRepositoryOwner() {
        return repositoryOwner;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public String getPullRequestId() {
        return pullRequestId;
    }


    public String getDestinationRepositoryOwner() {
        return destinationRepositoryOwner;
    }

    public String getDestinationRepositoryName() {
        return destinationRepositoryName;
    }

    public String getPullRequestTitle() {
        return pullRequestTitle;
    }

    public String getSourceCommitHash() { return sourceCommitHash; }

    public String getDestinationCommitHash() { return destinationCommitHash; }

    public String getBuildStartCommentId() { return buildStartCommentId; }

    public Map<String,String> getAdditionalParameters() { return additionalParameters; }
    
    @Override
    public String getShortDescription() {
        return "<a href=\"" + stashHost + "/projects/" + this.getDestinationRepositoryOwner() + "/repos/" +
                this.getDestinationRepositoryName() + "/pull-requests/" + this.getPullRequestId() +
                "/overview\" >PR #" + this.getPullRequestId() + " " + this.getPullRequestTitle() + " </a>";
    }
}

