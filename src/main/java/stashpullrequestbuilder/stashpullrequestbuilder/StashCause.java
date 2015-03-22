package stashpullrequestbuilder.stashpullrequestbuilder;

import hudson.model.Cause;

/**
 * Created by Nathan McCarthy
 */
public class StashCause extends Cause {
    private final String sourceBranch;
    private final String targetBranch;
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

    public StashCause(String stashHost,
                          String sourceBranch,
                          String targetBranch,
                          String repositoryOwner,
                          String repositoryName,
                          String pullRequestId,
                          String destinationRepositoryOwner,
                          String destinationRepositoryName,
                          String pullRequestTitle,
                          String sourceCommitHash,
                          String destinationCommitHash,
                          String buildStartCommentId) {
        this.sourceBranch = sourceBranch;
        this.targetBranch = targetBranch;
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
    }

    public String getSourceBranch() {
        return sourceBranch;
    }
    public String getTargetBranch() {
        return targetBranch;
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

    @Override
    public String getShortDescription() {
        return "<a href=\"" + stashHost + "/projects/" + this.getDestinationRepositoryOwner() + "/repos/" +
                this.getDestinationRepositoryName() + "/pull-requests/" + this.getPullRequestId() +
                "\" >PR #" + this.getPullRequestId() + " " + this.getPullRequestTitle() + " </a>";
    }
}

