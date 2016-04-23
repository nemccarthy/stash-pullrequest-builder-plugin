package stashpullrequestbuilder.stashpullrequestbuilder.stash;


public class StashCommitBuildStatus {

    private StashCommitBuildState state;
    private String key;
    private String name;
    private String url;
    private String description;

    public StashCommitBuildStatus(StashCommitBuildState state, String key, String name, String url, String description) {
        this.state = state;
        this.key = key;
        this.name = name;
        this.url = url;
        this.description = description;
    }

    public StashCommitBuildState getState() {
        return state;
    }

    public void setState(StashCommitBuildState state) {
        this.state = state;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }


    
}
