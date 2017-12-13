package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Created by Nathan McCarthy
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StashPullRequestResponseValue {
    private String description; //
    private Boolean locked; //

    private String title; //

    private StashPullRequestResponseValueRepository toRef;

    private Boolean closed; //

    private StashPullRequestResponseValueRepository fromRef;

    private String state; //
    private String createdDate; //
    private String updatedDate; //

    private String id; //

    private String version;

    private long lastCommentTimestamp;

    public String getDescription() {
        return description;
    }

    @JsonProperty("version")
    public String getVersion()
    {
        return version;
    }

    @JsonProperty("version")
    public void setVersion(String ver)
    {
        this.version = ver;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @JsonProperty("locked")
    public Boolean getLocked() {
        return locked;
    }

    @JsonProperty("locked")
    public void setLocked(Boolean locked) {
        this.locked = locked;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public StashPullRequestResponseValueRepository getToRef() {
        return toRef;
    }

    public void setToRef(StashPullRequestResponseValueRepository toRef) {
        this.toRef = toRef;
    }

    @JsonProperty("closed")
    public Boolean getClosed() {
        return closed;
    }

    @JsonProperty("closed")
    public void setClosed(Boolean closed) {
        this.closed = closed;
    }

    public StashPullRequestResponseValueRepository getFromRef() {
        return fromRef;
    }

    public void setFromRef(StashPullRequestResponseValueRepository fromRef) {
        this.fromRef = fromRef;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @JsonProperty("createdDate")
    public String getCreatedDate() {
        return createdDate;
    }

    @JsonProperty("createdDate")
    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
    }

    @JsonProperty("updatedDate")
    public String getUpdatedDate() {
        return updatedDate;
    }

    @JsonProperty("updatedDate")
    public void setUpdatedDate(String updatedDate) {
        this.updatedDate = updatedDate;
    }

    @JsonIgnore
    public long getLastCommentTimestamp() {
        return lastCommentTimestamp;
    }

    @JsonIgnore
    public void setLastCommentTimestamp(long lastCommentTimestamp) {
        this.lastCommentTimestamp = lastCommentTimestamp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

}
