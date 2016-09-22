package stashpullrequestbuilder.stashpullrequestbuilder;

import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
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

    public void onCompleted(AbstractBuild build, TaskListener listener) {
        StashCause cause = this.getCause(build);
        if (cause == null) {
            return;
        }
        Result result = build.getResult();
        JenkinsLocationConfiguration globalConfig = new JenkinsLocationConfiguration();
        String rootUrl = globalConfig.getUrl();
        String buildUrl = "";
        if (rootUrl == null) {
            buildUrl = " PLEASE SET JENKINS ROOT URL FROM GLOBAL CONFIGURATION " + build.getUrl();
        }
        else {
            buildUrl = rootUrl + build.getUrl();
        }
        repository.deletePullRequestComment(cause.getPullRequestId(), cause.getBuildStartCommentId());

        String additionalComment = "";
        StashBuildTrigger trig = StashBuildTrigger.getTrigger(build.getProject());
        if(trig.getMergeOnSuccess() == true && build.getResult() == Result.SUCCESS)
        {
            boolean mergeStat = repository.mergePullRequest(cause.getPullRequestId(), cause.getPullRequestVersion());
            if(mergeStat == true)
            {
                String logmsg = "Merged pull request " + cause.getPullRequestId() + "(" +
                cause.getSourceBranch() + ") to branch " + cause.getTargetBranch();
                logger.log(Level.INFO, logmsg);
                listener.getLogger().println(logmsg);
            }
            else
            {
                String logmsg = "Failed to merge pull request " + cause.getPullRequestId() + "(" +
                cause.getSourceBranch() + ") to branch " + cause.getTargetBranch() +
                " because it's out of date";
                logger.log(Level.INFO, logmsg);
                listener.getLogger().println(logmsg);
                additionalComment = additionalComment + "\n\n" + logmsg;
            }
        }

        StashPostBuildCommentAction comments = build.getAction(StashPostBuildCommentAction.class);
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
