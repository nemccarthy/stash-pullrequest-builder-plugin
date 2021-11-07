package stashpullrequestbuilder.stashpullrequestbuilder;

import hudson.model.InvisibleAction;

public class StashCauseWrapperAction extends InvisibleAction {
    private final StashCause stashCause;

    public StashCauseWrapperAction(StashCause stashCause) {
        this.stashCause = stashCause;
    }

    public StashCause getStashCause() {
        return this.stashCause;
    }
    
}
