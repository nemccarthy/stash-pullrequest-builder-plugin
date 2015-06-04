package stashpullrequestbuilder.stashpullrequestbuilder;

import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.Cause;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

/**
 * Created by Nathan McCarthy
 */
public class StashBuilds {
    private static final Logger logger = Logger.getLogger(StashBuilds.class.getName());
    private final StashRepository repository;

    public StashBuilds(StashRepository repository) {
        this.repository = repository;
    }

    public StashCause getCause(AbstractBuild build) {
        Cause cause = build.getCause(StashCause.class);
        if (cause == null || !(cause instanceof StashCause)) {
            return null;
        }
        return (StashCause) cause;
    }

    public void onStarted(AbstractBuild build) {
        StashCause cause = this.getCause(build);
        if (cause == null) {
            return;
        }
        try {
            build.setDescription(cause.getShortDescription());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Can't update build description", e);
        }
    }

    public void onCompleted(AbstractBuild build) {
        StashCause cause = this.getCause(build);
        if (cause == null) {
            return;
        }
        Result result = build.getResult();
        String rootUrl = Jenkins.getInstance().getRootUrl();
        String buildUrl = "";
        if (rootUrl == null) {
            buildUrl = " PLEASE SET JENKINS ROOT URL FROM GLOBAL CONFIGURATION " + build.getUrl();
        } else {
            buildUrl = rootUrl + build.getUrl();
        }
        repository.deletePullRequestComment(cause.getPullRequestId(), cause.getBuildStartCommentId());
        repository.postFinishedComment(cause.getPullRequestId(), cause.getSourceCommitHash(),
                cause.getDestinationCommitHash(), result == Result.SUCCESS, buildUrl, build.getNumber());
    }
}
