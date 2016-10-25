package stashpullrequestbuilder.stashpullrequestbuilder;

import static java.lang.String.format;

import hudson.model.Job;
import stashpullrequestbuilder.stashpullrequestbuilder.stash.StashPullRequestResponseValue;

import java.util.Collection;
import java.util.logging.Logger;

/**
 * Created by Nathan McCarthy
 */
public class StashPullRequestsBuilder {
    private static final Logger logger = Logger.getLogger(StashBuildTrigger.class.getName());
    private Job<?, ?> job;
    private StashBuildTrigger trigger;
    private StashRepository repository;
    private StashBuilds builds;

    public static StashPullRequestsBuilder getBuilder() {
        return new StashPullRequestsBuilder();
    }

    public void stop() {
        // TODO?
    }

    public void run() {
        logger.info(format("Build Start (%s).", job.getName()));
        this.repository.init();
        Collection<StashPullRequestResponseValue> targetPullRequests = this.repository.getTargetPullRequests();
        this.repository.addFutureBuildTasks(targetPullRequests);
    }

    public StashPullRequestsBuilder setupBuilder() {
        if (this.job == null || this.trigger == null) {
            throw new IllegalStateException();
        }
        this.repository = new StashRepository(this.trigger.getProjectPath(), this);
        this.builds = new StashBuilds(this.trigger, this.repository);
        return this;
    }

    public void setJob(Job<?, ?> job) {
        this.job = job;
    }

    public void setTrigger(StashBuildTrigger trigger) {
        this.trigger = trigger;
    }

    public Job<?, ?> getJob() {
        return this.job;
    }

    public StashBuildTrigger getTrigger() {
        return this.trigger;
    }

    public StashBuilds getBuilds() {
        return this.builds;
    }
}
