package stashpullrequestbuilder.stashpullrequestbuilder;

import antlr.ANTLRException;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Build;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.Job;
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
import jenkins.model.ParameterizedJobMixIn;

import net.sf.json.JSONObject;

import org.acegisecurity.Authentication;
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
@SuppressFBWarnings({"WMI_WRONG_MAP_ITERATOR", "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE"})
public class StashBuildTrigger extends Trigger<Job<?, ?>> {
    private static final Logger logger = Logger.getLogger(StashBuildTrigger.class.getName());

    @Extension
    public static final StashBuildTriggerDescriptor descriptor = new StashBuildTriggerDescriptor();

    transient private StashPullRequestsBuilder builder;
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
    private final boolean disableBuildComments;
    private final boolean deletePreviousBuildFinishComments;
    private final boolean cancelOutdatedJobsEnabled;

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
            boolean cancelOutdatedJobsEnabled,
            boolean disableBuildComments
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
        this.disableBuildComments = disableBuildComments;
    }

    @Override
    public void start(Job<?, ?> job, boolean newInstance) {
        try {
            this.builder = StashPullRequestsBuilder.getBuilder(job, this);
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "Cannot start trigger", e);
            return;
        }
        super.start(job, newInstance);
    }

    public static StashBuildTrigger getTrigger(Job job) {
        if (!(job instanceof ParameterizedJobMixIn.ParameterizedJob)) {
            return null;
        }

        ParameterizedJobMixIn.ParameterizedJob pjob = (ParameterizedJobMixIn.ParameterizedJob) job;
        Trigger trigger = pjob.getTriggers().get(descriptor);
        return (StashBuildTrigger) trigger;
    }

    public QueueTaskFuture<?> startJob(StashCause cause) {
        if (isCancelOutdatedJobsEnabled()) {
            cancelPreviousJobsInQueueThatMatch(cause);
            abortRunningJobsThatMatch(cause);
        }

        return new ParameterizedJobMixIn() {
            @Override
            protected Job asJob() {
                return StashBuildTrigger.this.job;
            }
        }.scheduleBuild2(0, new ParametersAction(getParameters(cause)), new CauseAction(cause));
    }

    private boolean hasCauseFromTheSamePullRequest(@Nullable List<Cause> causes, @Nullable StashCause pullRequestCause) {
        if (causes != null && pullRequestCause != null) {
            for (Cause cause : causes) {
                if (cause instanceof StashCause) {
                    StashCause sc = (StashCause) cause;
                    if (StringUtils.equalsIgnoreCase(sc.getPullRequestId(), pullRequestCause.getPullRequestId()) &&
                            StringUtils.equalsIgnoreCase(sc.getSourceRepositoryName(), pullRequestCause.getSourceRepositoryName())) {
                        return true;
                    }
                }
            }
        }
        return false;
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


    private List<ParameterValue> getParameters(StashCause cause) {
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
        if (additionalParameters != null) {
            for (String parameter : additionalParameters.keySet()) {
                values.add(new StringParameterValue(parameter, additionalParameters.get(parameter)));
            }
        }

        return values;
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
        Job job = builder.getJob();
        if (!job.isBuildable()) {
            logger.finer(format("%s: Skip analyze.", job.getFullDisplayName()));
        } else {
            logger.finer(format("%s: Start analyze", job.getFullDisplayName()));
            builder.run();
            logger.finer(format("%s: End analyze", job.getFullDisplayName()));
        }
        this.getDescriptor().save();
    }

    @Override
    public void stop() {
        super.stop();
    }


    public StashPullRequestsBuilder getBuilder() {
        return builder;
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
        Authentication defaultAuth = null;
        if (job instanceof Queue.Task) {
            defaultAuth = Tasks.getDefaultAuthenticationOf((Queue.Task) this.job);
        }
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        StandardUsernamePasswordCredentials.class,
                        this.job,
                        defaultAuth,
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

    public boolean isDisableBuildComments() {
        return disableBuildComments;
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
