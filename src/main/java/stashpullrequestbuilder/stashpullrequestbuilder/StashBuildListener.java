package stashpullrequestbuilder.stashpullrequestbuilder;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

import javax.annotation.Nonnull;
import java.util.logging.Logger;

/**
 * Created by Nathan McCarthy
 */
@Extension
public class StashBuildListener extends RunListener<Run<?, ?>> {
    private static final Logger logger = Logger.getLogger(StashBuildTrigger.class.getName());

    @Override
    public void onStarted(Run<?, ?> run, TaskListener listener) {
        logger.info("BuildListener onStarted called.");
        StashBuildTrigger trigger = StashBuildTrigger.getTrigger(run.getParent());
        if (trigger == null) {
            return;
        }
        trigger.getBuilder().getBuilds().onStarted(run);
    }

    @Override
    public void onCompleted(Run<?, ?> run, @Nonnull TaskListener listener) {
        StashBuildTrigger trigger = StashBuildTrigger.getTrigger(run.getParent());
        if (trigger == null) {
            return;
        }
        trigger.getBuilder().getBuilds().onCompleted(run, listener);
    }
}
