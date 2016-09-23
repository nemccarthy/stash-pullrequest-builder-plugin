package stashpullrequestbuilder.stashpullrequestbuilder;

import antlr.ANTLRException;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.Cause;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.StringParameterValue;
import hudson.model.queue.QueueTaskFuture;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;

/**
 * Created by Nathan McCarthy
 */
@SuppressWarnings({"WMI_WRONG_MAP_ITERATOR", "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE"})
public class StashBuildTrigger extends Trigger<AbstractProject<?, ?>> {
    private static final Logger logger = Logger.getLogger(StashBuildTrigger.class.getName());
    private final String projectPath;
    private final String cron;
    private final String stashHost;
    private final String credentialsId;
    private final String projectCode;
    private final String repositoryName;
    private final String ciSkipPhrases;
    private final String ciBuildPhrases;
    private final String targetBranchesToBuild;
    private final boolean ignoreSsl;
    private final boolean checkDestinationCommit;
    private final boolean checkMergeable;
    private final boolean mergeOnSuccess;
    private final boolean checkNotConflicted;
    private final boolean onlyBuildOnComment;
    private final boolean deletePreviousBuildFinishComments;
    private final boolean cancelOutdatedJobsEnabled;

    transient private StashPullRequestsBuilder stashPullRequestsBuilder;

    @Extension
    public static final StashBuildTriggerDescriptor descriptor = new StashBuildTriggerDescriptor();

    @DataBoundConstructor
    public StashBuildTrigger(
            String projectPath,
            String cron,
            String stashHost,
            String credentialsId,
            String projectCode,
            String repositoryName,
            String ciSkipPhrases,
            boolean ignoreSsl,
            boolean checkDestinationCommit,
            boolean checkMergeable,
            boolean mergeOnSuccess,
            boolean checkNotConflicted,
            boolean onlyBuildOnComment,
            String ciBuildPhrases,
            boolean deletePreviousBuildFinishComments,
            String targetBranchesToBuild,
            boolean cancelOutdatedJobsEnabled
    ) throws ANTLRException {
        super(cron);
        this.projectPath = projectPath;
        this.cron = cron;
        this.stashHost = stashHost;
        this.credentialsId = credentialsId;
        this.projectCode = projectCode;
        this.repositoryName = repositoryName;
        this.ciSkipPhrases = ciSkipPhrases;
        this.cancelOutdatedJobsEnabled = cancelOutdatedJobsEnabled;
        this.ciBuildPhrases = ciBuildPhrases == null ? "test this please" : ciBuildPhrases;
        this.ignoreSsl = ignoreSsl;
        this.checkDestinationCommit = checkDestinationCommit;
        this.checkMergeable = checkMergeable;
        this.mergeOnSuccess = mergeOnSuccess;
        this.checkNotConflicted = checkNotConflicted;
        this.onlyBuildOnComment = onlyBuildOnComment;
        this.deletePreviousBuildFinishComments = deletePreviousBuildFinishComments;
        this.targetBranchesToBuild = targetBranchesToBuild;
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

    // Needed for Jelly Config
    public String getcredentialsId() {
    	return this.credentialsId;
    }

    private StandardUsernamePasswordCredentials getCredentials() {
        return CredentialsMatchers.firstOrNull(
                          CredentialsProvider.lookupCredentials(
                                  StandardUsernamePasswordCredentials.class,
                                  this.job,
                                  Tasks.getDefaultAuthenticationOf(this.job),
                                  URIRequirementBuilder.fromUri(stashHost).build()
                          ),
                          CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialsId)));
    }

    public String getUsername() {
        return this.getCredentials().getUsername();
    }

    public String getPassword() {
        return this.getCredentials().getPassword().getPlainText();
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

    public boolean isIgnoreSsl() {
        return ignoreSsl;
    }

    public boolean getDeletePreviousBuildFinishComments() {
        return deletePreviousBuildFinishComments;
    }

    public String getTargetBranchesToBuild() {
        return targetBranchesToBuild;
    }

    public boolean getMergeOnSuccess() {
        return mergeOnSuccess;
    }

    public boolean isCancelOutdatedJobsEnabled() {
        return cancelOutdatedJobsEnabled;
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
        List<ParameterValue> values = getDefaultParameters();
        values.add(new StringParameterValue("sourceBranch", cause.getSourceBranch()));
        values.add(new StringParameterValue("targetBranch", cause.getTargetBranch()));
        values.add(new StringParameterValue("sourceRepositoryOwner", cause.getSourceRepositoryOwner()));
        values.add(new StringParameterValue("sourceRepositoryName", cause.getSourceRepositoryName()));
        values.add(new StringParameterValue("pullRequestId", cause.getPullRequestId()));
        values.add(new StringParameterValue("destinationRepositoryOwner", cause.getDestinationRepositoryOwner()));
        values.add(new StringParameterValue("destinationRepositoryName", cause.getDestinationRepositoryName()));
        values.add(new StringParameterValue("pullRequestTitle", cause.getPullRequestTitle()));
        values.add(new StringParameterValue("sourceCommitHash", cause.getSourceCommitHash()));
        values.add(new StringParameterValue("destinationCommitHash", cause.getDestinationCommitHash()));

        Map<String, String> additionalParameters = cause.getAdditionalParameters();
        if(additionalParameters != null){
        	for(String parameter : additionalParameters.keySet()){
        		values.add(new StringParameterValue(parameter, additionalParameters.get(parameter)));
        	}
        }

        if (isCancelOutdatedJobsEnabled()) {
            cancelPreviousJobsInQueueThatMatch(cause);
            abortRunningJobsThatMatch(cause);
        }

        return this.job.scheduleBuild2(0, cause, new ParametersAction(values));

    }

    private void cancelPreviousJobsInQueueThatMatch(@Nonnull StashCause stashCause) {
        logger.fine("Looking for queued jobs that match PR ID: " + stashCause.getPullRequestId());
        Queue queue = Jenkins.getInstance().getQueue();
        for (Queue.Item item : queue.getItems()) {
            if (hasCauseFromTheSamePullRequest(item.getCauses(), stashCause)) {
                logger.info("Canceling item in queue: " + item);
                queue.cancel(item);
            }
        }
    }

    private void abortRunningJobsThatMatch(@Nonnull StashCause stashCause) {
        logger.fine("Looking for running jobs that match PR ID: " + stashCause.getPullRequestId());
        for (Object o : job.getBuilds()) {
            if (o instanceof Build) {
                Build build = (Build) o;
                if (build.isBuilding() && hasCauseFromTheSamePullRequest(build.getCauses(), stashCause)) {
                    logger.info("Aborting build: " + build + " since PR is outdated");
                    build.getExecutor().interrupt(Result.ABORTED);
                }
            }
        }
    }

    private boolean hasCauseFromTheSamePullRequest(@Nullable List<Cause> causes, @Nullable StashCause pullRequestCause) {
        if (causes != null && pullRequestCause != null) {
            for (Cause cause : causes) {
                if (cause instanceof StashCause) {
                    StashCause sc = (StashCause) cause;
                    if (StringUtils.equals(sc.getPullRequestId(), pullRequestCause.getPullRequestId()) &&
                            StringUtils.equals(sc.getSourceRepositoryName(), pullRequestCause.getSourceRepositoryName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private List<ParameterValue> getDefaultParameters() {
        List<ParameterValue> values = new ArrayList<ParameterValue>();
        ParametersDefinitionProperty definitionProperty = this.job.getProperty(ParametersDefinitionProperty.class);
        if (definitionProperty != null) {
            for (ParameterDefinition definition : definitionProperty.getParameterDefinitions()) {
                values.add(definition.getDefaultParameterValue());
            }
        }
        return values;
    }

    @Override
    public void run() {
        if(this.getBuilder().getProject().isDisabled()) {
            logger.info(format("Build Skip (%s).", getBuilder().getProject().getName()));
        } else {
            logger.info(format("Build started (%s).", getBuilder().getProject().getName()));
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

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item context, @QueryParameter String source) {
            if (context == null || !context.hasPermission(Item.CONFIGURE)) {
                return new ListBoxModel();
            }
            return new StandardUsernameListBoxModel()
                    .includeEmptyValue()
                    .includeAs(context instanceof Queue.Task
                                    ? Tasks.getDefaultAuthenticationOf((Queue.Task) context)
                                    : ACL.SYSTEM, context, StandardUsernamePasswordCredentials.class,
                            URIRequirementBuilder.fromUri(source).build()
                    );
        }
    }
}
