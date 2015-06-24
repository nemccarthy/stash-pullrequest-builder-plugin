package stashpullrequestbuilder.stashpullrequestbuilder;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.model.*;
import hudson.model.queue.QueueTaskFuture;
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
    private final String username;
    private final String password;
    private final String projectCode;
    private final String repositoryName;
    private final String ciSkipPhrases;
    private final String ciBuildPhrases;
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
            String username,
            String password,
            String projectCode,
            String repositoryName,
            String ciSkipPhrases,
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
        this.username = username;
        this.password = password;
        this.projectCode = projectCode;
        this.repositoryName = repositoryName;
        this.ciSkipPhrases = ciSkipPhrases;
        this.ciBuildPhrases = ciBuildPhrases == null ? "test this please" : ciBuildPhrases;
        this.checkDestinationCommit = checkDestinationCommit;
        this.checkMergeable = checkMergeable;
        this.checkNotConflicted = checkNotConflicted;
        this.onlyBuildOnComment = onlyBuildOnComment;
    }

    public String getStashHost() {
        return stashHost;
    }

    public String getProjectPath() {
        return this.projectPath;
    }

    public String getCron() {
        return this.cron;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public String getCiSkipPhrases() {
        return ciSkipPhrases;
    }

    public String getCiBuildPhrases() {
        return ciBuildPhrases == null ? "test this please" : ciBuildPhrases;
    }

    public boolean getCheckDestinationCommit() {
    	return checkDestinationCommit;
    }

    @Override
    public void start(AbstractProject<?, ?> project, boolean newInstance) {
        try {
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

    public static StashBuildTrigger getTrigger(AbstractProject project) {
        Trigger trigger = project.getTrigger(StashBuildTrigger.class);
        return (StashBuildTrigger)trigger;
    }

    public StashPullRequestsBuilder getBuilder() {
        return this.stashPullRequestsBuilder;
    }

    public QueueTaskFuture<?> startJob(StashCause cause) {
        Map<String, ParameterValue> values = new HashMap<String, ParameterValue>();
        values.put("sourceBranch", new StringParameterValue("sourceBranch", cause.getSourceBranch()));
        values.put("targetBranch", new StringParameterValue("targetBranch", cause.getTargetBranch()));
        values.put("projectCode", new StringParameterValue("projectCode", cause.getRepositoryOwner()));
        values.put("repositoryName", new StringParameterValue("repositoryName", cause.getRepositoryName()));
        values.put("pullRequestId", new StringParameterValue("pullRequestId", cause.getPullRequestId()));
        values.put("destinationRepositoryOwner", new StringParameterValue("destinationRepositoryOwner", cause.getDestinationRepositoryOwner()));
        values.put("destinationRepositoryName", new StringParameterValue("destinationRepositoryName", cause.getDestinationRepositoryName()));
        values.put("pullRequestTitle", new StringParameterValue("pullRequestTitle", cause.getPullRequestTitle()));
        return this.job.scheduleBuild2(0, cause, new ParametersAction(new ArrayList(values.values())));
    }

    private Map<String, ParameterValue> getDefaultParameters() {
        Map<String, ParameterValue> values = new HashMap<String, ParameterValue>();
        ParametersDefinitionProperty definitionProperty = this.job.getProperty(ParametersDefinitionProperty.class);

        if (definitionProperty != null) {
            for (ParameterDefinition definition : definitionProperty.getParameterDefinitions()) {
                values.put(definition.getName(), definition.getDefaultParameterValue());
            }
        }
        return values;
    }

    @Override
    public void run() {
        if(this.getBuilder().getProject().isDisabled()) {
            logger.info("Build Skip.");
        } else {
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
