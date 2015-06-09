package stashpullrequestbuilder.stashpullrequestbuilder;

import hudson.model.InvisibleAction;

public class StashPostBuildCommentAction extends InvisibleAction {
    private final String buildSuccessfulComment;
    private final String buildFailedComment;

    public StashPostBuildCommentAction(String buildSuccessfulComment, String buildFailedComment) {
        this.buildSuccessfulComment = buildSuccessfulComment;
        this.buildFailedComment = buildFailedComment;
    }

    public String getBuildSuccessfulComment() {
        return this.buildSuccessfulComment;
    }

    public String getBuildFailedComment() {
        return this.buildFailedComment;
    }
}
