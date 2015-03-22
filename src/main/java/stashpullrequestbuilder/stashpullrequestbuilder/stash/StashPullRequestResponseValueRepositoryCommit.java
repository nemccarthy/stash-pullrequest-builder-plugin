package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StashPullRequestResponseValueRepositoryCommit {
    private String latestChangeset;

    public String getHash() {
        return latestChangeset;
    }

    public void setHash(String hash) {
        this.latestChangeset = hash;
    }
}