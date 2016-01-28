package stashpullrequestbuilder.stashpullrequestbuilder;

import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Result;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by Nathan McCarthy
 */
public class StashBuilds {
    private static final Logger logger = Logger.getLogger(StashBuilds.class.getName());
    private StashBuildTrigger trigger;
    private StashRepository repository;

    public StashBuilds(StashBuildTrigger trigger, StashRepository repository) {
        this.trigger = trigger;
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
        }
        else {
            buildUrl = rootUrl + build.getUrl();
        }
        repository.deletePullRequestComment(cause.getPullRequestId(), cause.getBuildStartCommentId());

        StashPostBuildCommentAction comments = build.getAction(StashPostBuildCommentAction.class);
        String additionalComment = "";
        if(comments != null) {
            String buildComment = result == Result.SUCCESS ? comments.getBuildSuccessfulComment() : comments.getBuildFailedComment();

            if(buildComment != null && !buildComment.isEmpty()) {
              additionalComment = "\n\n" + buildComment;
            }
        }
        String duration = build.getDurationString();
        repository.postFinishedComment(cause.getPullRequestId(), cause.getSourceCommitHash(),
                cause.getDestinationCommitHash(), result, buildUrl,
                build.getNumber(), additionalComment, duration);
    }
}
