package stashpullrequestbuilder.stashpullrequestbuilder.stash;

public enum StashCommitBuildState {

    SUCCESSFUL("SUCCESSFUL"),
    FAILED("FAILED"),
    INPROGRESS("INPROGRESS");

    private String state;

    StashCommitBuildState(String state){
        this.state = state;
    }

    public String getState() {
        return state;
    }
}
