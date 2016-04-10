package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import java.io.Serializable;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author blaffoy
 */
public class StashPullRequestBuildHistory implements Serializable {
    private static final Logger logger = Logger.getLogger(StashPullRequestBuildHistory.class.getName());

    private final HashSet<Merge> mergeTriggerHistory;
    private final HashSet<Integer> commentTriggerHistory;

    private class Merge {
        public final String branch;
        public final String target;

        public Merge(String branch, String target) {
            this.branch = branch;
            this.target = target;
        }

        @Override
        public String toString() {
            return "branch: \"" + branch + " ; target: \"" + target + "\"";
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Merge))
                return false;
            if (obj == this)
                return true;

            Merge rhs = (Merge) obj;
            return branch.equals(rhs.branch) && target.equals(rhs.target);
        }

        @Override
        public int hashCode() {
            int hash = 23;
            int mult = 31;
            hash = this.branch.hashCode() + hash * mult;
            hash = this.target.hashCode() + hash * mult;
            return hash;
        }
    }

    public StashPullRequestBuildHistory() {
        logger.log(Level.INFO, "Setting up new Build History");
        this.mergeTriggerHistory = new HashSet<Merge>();
        this.commentTriggerHistory = new HashSet<Integer>();
    }

    public void saveMergeTrigger(String branchSha, String targetSha) {
        Merge m = new Merge(branchSha, targetSha);
        String mth = mergeTriggerHistory.toString();
        if (mergeHasBeenBuilt(m)) {
            logger.log(Level.SEVERE, "Merge trigger history already contains {0}", m);
        } else {
            mergeTriggerHistory.add(m);
        }
    }

    private boolean mergeHasBeenBuilt(Merge m) {
        return mergeTriggerHistory.contains(m);
    }

    public boolean mergeHasBeenBuilt(String branchSha, String targetSha) {
        return mergeTriggerHistory.contains(new Merge(branchSha, targetSha));
    }

    public void saveCommentTrigger(Integer commentId) {
        String cth = commentTriggerHistory.toString();
        if (commentHasBeenBuilt(commentId)) {
            logger.log(Level.SEVERE, "Comment trigger history already contains {0}", commentId);
        } else {
            commentTriggerHistory.add(commentId);
        }
    }

    public boolean commentHasBeenBuilt(Integer commentId) {
        return commentTriggerHistory.contains(commentId);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("Comment Trigger History:\n");
        sb.append(commentTriggerHistory.toString());
        sb.append("\n");
        sb.append("Merge Trigger History:\n");
        sb.append(mergeTriggerHistory.toString());
        sb.append("\n");

        return sb.toString();
    }
}