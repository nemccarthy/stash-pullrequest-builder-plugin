package stashpullrequestbuilder.stashpullrequestbuilder.stash;

/**
 * Created by nathan on 20/03/2015.
 */
public class ApiTest {

    public static void main(String [] args) {
        StashApiClient api = new StashApiClient("https://git.int.quantium.com.au", "cs-build", "b1gd4t4!build", "CHT", "checkout-bi");
        api.getPullRequests();
        api.getPullRequestComments("CHT", "checkout-bi", "6");
//        api.postPullRequestComment("6", "Test post comment");
//        api.postPullRequestComment("6","[*BuildFinished* **mirror-test**] 327a8f77ffab4e817368d74ce66fd827ea975404 into 0a8d7c9ca5f00f9767f1d2224e40dcfe2de0211c \n" +
//                "\n" +
//                " **✕ FAILURE** - https://ci.build.quantium.com.au/job/mirror-test/3/");
    }

}
