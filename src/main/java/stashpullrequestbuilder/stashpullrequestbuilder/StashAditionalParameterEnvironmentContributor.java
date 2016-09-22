package stashpullrequestbuilder.stashpullrequestbuilder;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.*;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;

@Extension
public class StashAditionalParameterEnvironmentContributor extends EnvironmentContributor {
    private static Set<String> params =
            new HashSet<String>(Arrays.asList("sourceBranch",
                    "targetBranch",
                    "sourceRepositoryOwner",
                    "sourceRepositoryName",
                    "pullRequestId",
                    "destinationRepositoryOwner",
                    "destinationRepositoryName",
                    "pullRequestTitle",
                    "sourceCommitHash",
                    "destinationCommitHash"));

    @Override
    public void buildEnvironmentFor(@Nonnull Run r, @Nonnull EnvVars envs, @Nonnull TaskListener listener) throws IOException, InterruptedException {
        StashCause cause = (StashCause) r.getCause(StashCause.class);
        if (cause == null) {
            return;
        }
        ParametersAction pa = r.getAction(ParametersAction.class);
        for (String param : params) {
            addParameter(param, pa, envs);
        }
        super.buildEnvironmentFor(r, envs, listener);
    }

    private static void addParameter(String key,
                                     ParametersAction pa,
                                     EnvVars envs) {
        ParameterValue pv = pa.getParameter(key);
        if (pv == null || !(pv instanceof StringParameterValue)) {
            return;
        }
        StringParameterValue value = (StringParameterValue) pa.getParameter(key);
        envs.put(key, getString(value.value, ""));
    }

    private static String getString(String actual,
                                    String d) {
        return actual == null ? d : actual;
    }


}
