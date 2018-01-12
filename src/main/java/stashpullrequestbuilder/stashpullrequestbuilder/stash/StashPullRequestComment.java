package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang.builder.CompareToBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;
import org.joda.time.DateTime;

/**
 * Created by Nathan McCarthy
 */
@SuppressFBWarnings("EQ_COMPARETO_USE_OBJECT_EQUALS")
@JsonIgnoreProperties(ignoreUnknown = true)
public class StashPullRequestComment implements Comparable<StashPullRequestComment> {

    private Integer commentId;//
    private String text;
    private DateTime createdDate;

    @JsonProperty("id")
    public Integer getCommentId() {
        return commentId;
    }

    @JsonProperty("id")
    public void setCommentId(Integer commentId) {
        this.commentId = commentId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public DateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(DateTime createdDate) {
        this.createdDate = createdDate;
    }

    public int compareTo(StashPullRequestComment target) {
        return new CompareToBuilder()
                .append(this.createdDate, target.createdDate)
                .append(this.commentId, target.commentId)
                .toComparison();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("createdDate", createdDate)
                .append("commentId", commentId)
                .append("text", text)
                .toString();
    }
}
