package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

import java.util.List;

/**
 * Created by Nathan McCarthy
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StashPullRequestActivityResponse {
    @JsonIgnore
    private List<StashPullRequestActivity> prValues;

    @JsonProperty("size")
    private Integer size;//

    private boolean isLastPage;

    private int nextPageStart;

    @JsonProperty("values")
    public List<StashPullRequestActivity> getPrValues() {
        return prValues;
    }

    @JsonProperty("values")
    public void setPrValues(List<StashPullRequestActivity> prValues) {
        this.prValues = prValues;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public Boolean getIsLastPage() {
        return isLastPage;
    }

    public void setIsLastPage(Boolean isLastPage) {
        this.isLastPage = isLastPage;
    }

    public Integer getNextPageStart() {
        return nextPageStart;
    }

    public void setNextPageStart(Integer nextPageStart) {
        this.nextPageStart = nextPageStart;
    }

}
