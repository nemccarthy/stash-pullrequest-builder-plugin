package stashpullrequestbuilder.stashpullrequestbuilder;

import hudson.model.Job;

/**
 * Created by Nathan McCarthy
 */
public class StashPullRequestsBuilder {
    private Job<?, ?> job;
    private StashBuildTrigger trigger;
    private StashRepository repository;
    private StashBuilds builds;

    public static StashPullRequestsBuilder getBuilder(Job<?, ?> job, StashBuildTrigger trigger) {
        StashPullRequestsBuilder builder = new StashPullRequestsBuilder();
        builder.job = job;
        builder.trigger = trigger;
        return builder.setupBuilder();
    }

    public void run() {
        this.repository.init();
        this.repository.buildPullRequests(this.repository.getBuildablePullRequests());
    }

    public void stop() {
        // TODO?
    }

    protected StashPullRequestsBuilder setupBuilder() {
        if (this.job == null || this.trigger == null) {
            throw new IllegalStateException();
        }
        this.repository = new StashRepository(this.trigger.getProjectPath(), this);
        this.builds = new StashBuilds(this.trigger, this.repository);
        return this;
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
