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
    private final String stashHost;
    private final String stashUsername;
    private final String stashPassword;
    private final String targetBranchFilter;
    private final String ciSkipPhrases;
    private final String ciBuildPhrases;
    private final boolean ignoreSsl;
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
            String stashHost,
            String stashUsername,
            String stashPassword,
            String targetBranchFilter,
            String ciSkipPhrases,
            boolean ignoreSsl,
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
        this.stashHost = stashHost;
        this.stashUsername = stashUsername;
        this.stashPassword = stashPassword;
        this.targetBranchFilter = targetBranchFilter;
        this.ciSkipPhrases = ciSkipPhrases;
        this.ciBuildPhrases = ciBuildPhrases == null ? "test this please" : ciBuildPhrases;
        this.ignoreSsl = ignoreSsl;
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

    public String getStashHost() {
		return stashHost;
	}

	public String getStashUsername() {
		return stashUsername;
	}

	public String getStashPassword() {
		return stashPassword;
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

    public boolean isIgnoreSsl() {
        return ignoreSsl;
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
        Map<String, ParameterValue> values = new HashMap<String, ParameterValue>();
        values.put("sourceBranch", new StringParameterValue("sourceBranch", cause.getSourceBranch()));
        values.put("targetBranch", new StringParameterValue("targetBranch", cause.getTargetBranch()));
        values.put("pullRequest", new StringParameterValue("pullRequest", cause.getPullRequestBranch()));
        values.put("projectCode", new StringParameterValue("projectCode", cause.getRepositoryOwner()));
        values.put("repositoryName", new StringParameterValue("repositoryName", cause.getRepositoryName()));
        values.put("pullRequestId", new StringParameterValue("pullRequestId", cause.getPullRequestId()));
        values.put("destinationRepositoryOwner", new StringParameterValue("destinationRepositoryOwner", cause.getDestinationRepositoryOwner()));
        values.put("destinationRepositoryName", new StringParameterValue("destinationRepositoryName", cause.getDestinationRepositoryName()));
        values.put("pullRequestTitle", new StringParameterValue("pullRequestTitle", cause.getPullRequestTitle()));
        values.put("sourceCommitHash", new StringParameterValue("sourceCommitHash", cause.getSourceCommitHash()));
        
        Map<String, String> additionalParameters = cause.getAdditionalParameters();
        if(additionalParameters != null){
        	for(String parameter : additionalParameters.keySet()){
        		values.put(parameter, new StringParameterValue(parameter, additionalParameters.get(parameter)));
        	}
        }
        
        return this.job.scheduleBuild2(0, cause, new ParametersAction(new ArrayList<ParameterValue>(values.values())));
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
