package stashpullrequestbuilder.stashpullrequestbuilder;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.model.*;
import hudson.model.queue.QueueTaskFuture;
import hudson.plugins.git.GitSCM;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by Nathan McCarthy
 */
public class StashBuildTrigger extends Trigger<AbstractProject<?, ?>> {
    private static final Logger logger = Logger.getLogger(StashBuildTrigger.class.getName());
    private final String projectPath;
    private final String cron;
    private final String targetBranchFilter;
    private final String ciSkipPhrases;
    private final String ciBuildPhrases;
    private final boolean checkMergeBeforeBuild;
    private final boolean checkDestinationCommit;
    private final boolean checkMergeable;
    private final boolean checkNotConflicted;
    private final boolean onlyBuildOnComment;

    transient private StashPullRequestsBuilder stashPullRequestsBuilder;

    @Extension
    public static final StashBuildTriggerDescriptor descriptor = new StashBuildTriggerDescriptor();

    @DataBoundConstructor
    public StashBuildTrigger(
            String projectPath,
            String cron,
            String targetBranchFilter,
            String ciSkipPhrases,
            boolean checkMergeBeforeBuild,
            boolean checkDestinationCommit,
            boolean checkMergeable,
            boolean checkNotConflicted,
            boolean onlyBuildOnComment,
            String ciBuildPhrases
            ) throws ANTLRException {
        super(cron);
        this.projectPath = projectPath;
        this.cron = cron;
        this.targetBranchFilter = targetBranchFilter;
        this.ciSkipPhrases = ciSkipPhrases;
        this.ciBuildPhrases = ciBuildPhrases == null ? "test this please" : ciBuildPhrases;
        this.checkMergeBeforeBuild = checkMergeBeforeBuild;
        this.checkDestinationCommit = checkDestinationCommit;
        this.checkMergeable = checkMergeable;
        this.checkNotConflicted = checkNotConflicted;
        this.onlyBuildOnComment = onlyBuildOnComment;
    }

    public String getProjectPath() {
        return this.projectPath;
    }

    public String getCron() {
        return this.cron;
    }

    public String getTargetBranchFilter() {
		return targetBranchFilter;
	}

	public String getCiSkipPhrases() {
        return ciSkipPhrases;
    }

    public String getCiBuildPhrases() {
        return ciBuildPhrases == null ? "test this please" : ciBuildPhrases;
    }

    public boolean isCheckMergeBeforeBuild() {
		return checkMergeBeforeBuild;
	}

	public boolean getCheckDestinationCommit() {
    	return checkDestinationCommit;
    }

    @Override
    public void start(AbstractProject<?, ?> project, boolean newInstance) {
        try {
        	if (!(project.getScm() instanceof GitSCM))
        		throw new IllegalStateException("No git SCM defined");
        	
            this.stashPullRequestsBuilder = StashPullRequestsBuilder.getBuilder();
            this.stashPullRequestsBuilder.setProject(project);
            this.stashPullRequestsBuilder.setTrigger(this);
            this.stashPullRequestsBuilder.setupBuilder();
        } catch(IllegalStateException e) {
            logger.log(Level.SEVERE, "Can't start trigger", e);
            return;
        }
        super.start(project, newInstance);
    }

    public static StashBuildTrigger getTrigger(AbstractProject<?, ?> project) {
        Trigger<?> trigger = project.getTrigger(StashBuildTrigger.class);
        return (StashBuildTrigger)trigger;
    }

    public StashPullRequestsBuilder getBuilder() {
        return this.stashPullRequestsBuilder;
    }

    public QueueTaskFuture<?> startJob(StashCause cause) {
    	ArrayList<ParameterValue> parameterList = new ArrayList<ParameterValue>();
    	parameterList.add(new StringParameterValue("sourceProject", cause.getRepositoryOwner()));
    	parameterList.add(new StringParameterValue("sourceRepository", cause.getRepositoryName()));
    	parameterList.add(new StringParameterValue("sourceBranch", cause.getSourceBranch()));
    	parameterList.add(new StringParameterValue("targetProject", cause.getDestinationRepositoryOwner()));
    	parameterList.add(new StringParameterValue("targetRepository", cause.getDestinationRepositoryName()));
    	parameterList.add(new StringParameterValue("targetBranch", cause.getTargetBranch()));
    	parameterList.add(new StringParameterValue("pullRequest", cause.getPullRequestBranch()));
        parameterList.add(new StringParameterValue("pullRequestId", cause.getPullRequestId()));
    	parameterList.add(new StringParameterValue("pullRequestTitle", cause.getPullRequestTitle()));
    	parameterList.add(new StringParameterValue("pullRequestCommit", cause.getSourceCommitHash()));
    	return this.job.scheduleBuild2(0, cause, new ParametersAction(parameterList));
    }

    @Override
    public void run() {
    	if(this.getBuilder().getProject().isDisabled()) {
            logger.info("Build Skip.");
        } else {
        	if (!(this.getBuilder().getProject().getScm() instanceof GitSCM))
        		throw new IllegalStateException("No git SCM defined");
        	
            this.stashPullRequestsBuilder.run();
        }
        this.getDescriptor().save();
    }

    @Override
    public void stop() {
        super.stop();
    }

    public boolean isCheckMergeable() {
        return checkMergeable;
    }

    public boolean isCheckNotConflicted() {
        return checkNotConflicted;
    }

    public boolean isOnlyBuildOnComment() {
        return onlyBuildOnComment;
    }

    public static final class StashBuildTriggerDescriptor extends TriggerDescriptor {
        public StashBuildTriggerDescriptor() {
            load();
        }

        @Override
        public boolean isApplicable(Item item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Stash Pull Requests Builder";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            save();
            return super.configure(req, json);
        }
    }
}
