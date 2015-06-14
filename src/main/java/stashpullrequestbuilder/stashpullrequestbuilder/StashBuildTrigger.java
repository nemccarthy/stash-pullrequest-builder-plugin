package stashpullrequestbuilder.stashpullrequestbuilder;

import antlr.ANTLRException;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import hudson.model.queue.QueueTaskFuture;
import hudson.security.ACL;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private final String credentialsId;
    private final String projectCode;
    private final String repositoryName;
    private final String ciSkipPhrases;
    private final String ciBuildPhrases;
    private final boolean checkDestinationCommit;
    private final boolean checkMergeable;
    private final boolean checkNotConflicted;
    private final boolean onlyBuildOnComment;
    private final StandardUsernamePasswordCredentials credentials;

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
            String credentialsId,
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
        this.credentialsId = credentialsId;
        this.projectCode = projectCode;
        this.repositoryName = repositoryName;
        this.ciSkipPhrases = ciSkipPhrases;
        this.ciBuildPhrases = ciBuildPhrases == null ? "test this please" : ciBuildPhrases;
        this.checkDestinationCommit = checkDestinationCommit;
        this.checkMergeable = checkMergeable;
        this.checkNotConflicted = checkNotConflicted;
        this.onlyBuildOnComment = onlyBuildOnComment;

        if (credentialsId != null && credentialsId.trim().length() > 0) {
            this.credentials = getCredentials();
        } else {
            this.credentials = null;
        }

        if (isCredentialsSupplied()) {
            logger.info("Using Credentials from Credentials plugin.");
        }
        if (!isCredentialsSupplied() || StringUtils.isNotEmpty(username) || StringUtils.isNotEmpty(password)) {
            logger.warning("Stash credentials not supplied from Credentials plugin, please consider using the Credentials plugin!");
        }
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
        if (isCredentialsSupplied())
            return credentials.getUsername();
        else
            return username;
    }

    public String getPassword() {
        if (isCredentialsSupplied())
            return Secret.toString(credentials.getPassword());
        else
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

    public String getCredentialsId() {
        return credentialsId;
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

    private StandardUsernamePasswordCredentials getCredentials() {
        if (credentialsId != null) {
            for (StandardUsernamePasswordCredentials c : availableCredentials(this.job, stashHost)) {
                if (c.getId().equals(credentialsId)) {
                    return c;
                }
            }
        }
        return null;
    }

    private List<? extends StandardUsernamePasswordCredentials> availableCredentials(Job<?,?> owner, String source) {
        return CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, owner, ACL.SYSTEM, URIRequirementBuilder.fromUri(source).build());
    }

    private boolean isCredentialsSupplied() {
        return credentialsId != null && credentials != null;
    }


    @Extension
    public static final class DescriptorImpl extends Descriptor<StashBuildTrigger> {

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Job<?,?> owner) {
            if (owner == null || !owner.hasPermission(Item.CONFIGURE)) {
                return new ListBoxModel();
            }
            return new StandardUsernameListBoxModel().withAll(availableCredentials(owner, stas));
        }

        public FormValidation doCheckCredentialsId(@QueryParameter("credentialsId") String apiKey) {
            if (apiKey == null || apiKey.length() == 0) {
                return FormValidation.error("Missing API Key");
            }
            return FormValidation.ok();
        }


        @Override
        public String getDisplayName() {
            return "Deployment Notification";
        }
    }


}
