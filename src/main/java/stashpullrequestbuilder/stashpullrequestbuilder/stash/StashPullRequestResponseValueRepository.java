package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Created by Nathan McCarthy
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StashPullRequestResponseValueRepository {
    private StashPullRequestResponseValueRepositoryRepository repository;

    @JsonIgnore
    private StashPullRequestResponseValueRepositoryBranch branch;

    @JsonIgnore
    private StashPullRequestResponseValueRepositoryCommit commit;

    private String latestChangeset;
    private String id;


    @JsonProperty("id")
    public String getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(String id) { //TODO
        this.id = id;
        this.branch = new StashPullRequestResponseValueRepositoryBranch();
        String[] ref = id.split("/");
        if (ref.length > 0) {
            this.branch.setName(ref[ref.length - 1]);
        }
    }

    @JsonProperty("latestChangeset")
    public String getLatestChangeset() {
        return latestChangeset;
    }

    @JsonProperty("latestChangeset")
    public void setLatestChangeset(String latestChangeset) { //TODO
        this.latestChangeset = latestChangeset;
        this.commit = new StashPullRequestResponseValueRepositoryCommit();
        this.commit.setHash(latestChangeset);
    }

    @JsonProperty("repository")
    public StashPullRequestResponseValueRepositoryRepository getRepository() {
        return repository;
    }

    @JsonProperty("repository")
    public void setRepository(StashPullRequestResponseValueRepositoryRepository repository) {
        this.repository = repository;
    }

    @JsonProperty("branch")
    public StashPullRequestResponseValueRepositoryBranch getBranch() {
        return branch;
    }

    @JsonProperty("branch")
    public void setBranch(StashPullRequestResponseValueRepositoryBranch branch) {
        this.branch = branch;
    }

    public StashPullRequestResponseValueRepositoryCommit getCommit() {
        return commit;
    }

    public void setCommit(StashPullRequestResponseValueRepositoryCommit commit) {
        this.commit = commit;
    }
}


