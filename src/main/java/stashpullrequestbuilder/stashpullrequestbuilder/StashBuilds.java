package stashpullrequestbuilder.stashpullrequestbuilder;

import hudson.model.Cause;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.JenkinsLocationConfiguration;

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

    public StashCause getCause(Run run) {
        Cause cause = run.getCause(StashCause.class);
        if (cause == null || !(cause instanceof StashCause)) {
            return null;
        }
        return (StashCause) cause;
    }

    public void onStarted(Run run) {
        StashCause cause = this.getCause(run);
        if (cause == null) {
            return;
        }
        try {
            run.setDescription(cause.getShortDescription());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Can't update build description", e);
        }
    }

    public void onCompleted(Run run, TaskListener listener) {
        StashBuildTrigger trig = StashBuildTrigger.getTrigger(run.getParent());
        StashCause cause = this.getCause(run);

        if (cause == null) {
            return;
        }

        // Add Comment
        if (!trig.isDisableBuildComments()) {
            JenkinsLocationConfiguration globalConfig = new JenkinsLocationConfiguration();
            String rootUrl = globalConfig.getUrl();
            String buildUrl = (rootUrl != null ? rootUrl : " PLEASE SET JENKINS ROOT URL FROM GLOBAL CONFIGURATION ") + run.getUrl();
            Result result = run.getResult();
            String comment = "";

            StashPostBuildCommentAction additionalComments = run.getAction(StashPostBuildCommentAction.class);
            if (additionalComments != null) {
                String buildComment = result == Result.SUCCESS ? additionalComments.getBuildSuccessfulComment() : additionalComments.getBuildFailedComment();

                if (buildComment != null && !buildComment.isEmpty()) {
                    comment = "\n\n" + buildComment;
                }
            }

            String duration = run.getDurationString();
            repository.deletePullRequestComment(cause.getPullRequestId(), cause.getBuildStartCommentId());
            repository.postFinishedComment(cause.getPullRequestId(), cause.getSourceCommitHash(),
                    cause.getDestinationCommitHash(), result, buildUrl,
                    run.getNumber(), comment, duration);
        }

        //Merge PR
        if (trig.getMergeOnSuccess() && run.getResult() == Result.SUCCESS) {
            boolean mergeStat = repository.mergePullRequest(cause.getPullRequestId(), cause.getPullRequestVersion());
            if (mergeStat == true) {
                String logmsg = "Merged pull request " + cause.getPullRequestId() + "(" +
                        cause.getSourceBranch() + ") to branch " + cause.getTargetBranch();
                logger.log(Level.INFO, logmsg);
                listener.getLogger().println(logmsg);
            } else {
                String logmsg = "Failed to merge pull request " + cause.getPullRequestId() + "(" +
                        cause.getSourceBranch() + ") to branch " + cause.getTargetBranch() +
                        " because it's out of date";
                logger.log(Level.INFO, logmsg);
                listener.getLogger().println(logmsg);
            }
        }
    }
}
