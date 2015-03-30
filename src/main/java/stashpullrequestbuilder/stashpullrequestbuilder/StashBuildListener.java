package stashpullrequestbuilder.stashpullrequestbuilder;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

import javax.annotation.Nonnull;
import java.util.logging.Logger;

/**
 * Created by Nathan McCarthy
 */
@Extension
public class StashBuildListener extends RunListener<AbstractBuild> {
    private static final Logger logger = Logger.getLogger(StashBuildTrigger.class.getName());

    @Override
    public void onStarted(AbstractBuild abstractBuild, TaskListener listener) {
        logger.info("BuildListener onStarted called.");
        StashBuildTrigger trigger = StashBuildTrigger.getTrigger(abstractBuild.getProject());
        if (trigger == null) {
            return;
        }
        trigger.getBuilder().getBuilds().onStarted(abstractBuild);
    } 
   
    @Override
    public void onCompleted(AbstractBuild abstractBuild, @Nonnull TaskListener listener) {
        StashBuildTrigger trigger = StashBuildTrigger.getTrigger(abstractBuild.getProject());
        if (trigger == null) {
            return;
        }
        trigger.getBuilder().getBuilds().onCompleted(abstractBuild);
    }
}
