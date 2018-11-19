package stashpullrequestbuilder.stashpullrequestbuilder;

import javax.annotation.Nonnull;

import jenkins.model.CauseOfInterruption;

public class StashCauseofInterruption extends CauseOfInterruption {
    private static final long serialVersionUID = 1L;

    private final String pullRequestId;
    private final String pullRequestTitle;
    private final String pullRequestVersion;
    private final String sourceBranch;
    private final String sourceCommitHash;
    private final String sourceRepositoryOwner;
    private final String sourceRepositoryName;
    private final String destinationBranch;
    private final String destinationCommitHash;
    private final String destinationRepositoryOwner;
    private final String destinationRepositoryName;

    public StashCauseofInterruption(@Nonnull StashCause stashCause) {
        this(stashCause.getPullRequestId(), stashCause.getPullRequestTitle(), stashCause.getPullRequestVersion(), stashCause.getSourceBranch(), stashCause.getSourceCommitHash(), stashCause.getSourceRepositoryOwner(), stashCause.getSourceRepositoryName(), stashCause.getTargetBranch(), stashCause.getDestinationCommitHash(), stashCause.getDestinationRepositoryOwner(), stashCause.getDestinationRepositoryName());
    }

    public StashCauseofInterruption(String pullRequestId, String pullRequestTitle, String pullRequestVersion, String sourceBranch, String sourceCommitHash, String sourceRepositoryOwner, String sourceRepositoryName, String destinationBranch, String destinationCommitHash, String destinationRepositoryOwner, String destinationRepositoryName) {
        super();
        this.pullRequestId = pullRequestId;
        this.pullRequestTitle = pullRequestTitle;
        this.pullRequestVersion = pullRequestVersion;
        this.sourceBranch = sourceBranch;
        this.sourceCommitHash = sourceCommitHash;
        this.sourceRepositoryOwner = sourceRepositoryOwner;
        this.sourceRepositoryName = sourceRepositoryName;
        this.destinationBranch = destinationBranch;
        this.destinationCommitHash = destinationCommitHash;
        this.destinationRepositoryOwner = destinationRepositoryOwner;
        this.destinationRepositoryName = destinationRepositoryName;
    }

    public String getPullRequestId() {
        return pullRequestId;
    }

    public String getPullRequestTitle() {
        return pullRequestTitle;
    }

    public String getPullRequestVersion() {
        return pullRequestVersion;
    }

    public String getSourceBranch() {
        return sourceBranch;
    }

    public String getSourceCommitHash() {
        return sourceCommitHash;
    }

    public String getSourceRepositoryOwner() {
        return sourceRepositoryOwner;
    }

    public String getSourceRepositoryName() {
        return sourceRepositoryName;
    }

    public String getDestinationBranch() {
        return destinationBranch;
    }

    public String getDestinationCommitHash() {
        return destinationCommitHash;
    }

    public String getDestinationRepositoryOwner() {
        return destinationRepositoryOwner;
    }

    public String getDestinationRepositoryName() {
        return destinationRepositoryName;
    }

    @Override
    public String getShortDescription() {
        return "Aborted outdated job for PR #" + getPullRequestId() + " " + getPullRequestTitle();
    }

}

