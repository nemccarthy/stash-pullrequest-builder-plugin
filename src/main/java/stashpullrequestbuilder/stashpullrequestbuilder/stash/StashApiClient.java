package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.jgit.transport.URIish;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by Nathan McCarthy
 */
public class StashApiClient {
    private static final Logger logger = Logger.getLogger(StashApiClient.class.getName());

    private final String apiBaseUrl;
    private final String host;
    private final String project;
    private final String repositoryName;
    private final Credentials credentials;

    public StashApiClient(URIish stashUri, StandardUsernamePasswordCredentials credentials) {
    	if (credentials != null) {
	        this.credentials = new UsernamePasswordCredentials(
	        		credentials.getUsername(), 
	        		credentials.getPassword().getPlainText());
    	} else {
    		this.credentials = null;
    	}
        
        if (stashUri == null || !stashUri.getPath().startsWith("/scm/")) {
        	throw new IllegalArgumentException("Invalid stash URI " + stashUri);
        }
        
        // split on / after removing prefix /scm/ should give the project name as first entry       
        this.project = stashUri.getPath().substring(5).split("/")[0];
        this.repositoryName = stashUri.getHumanishName();
        this.host = stashUri.getScheme() + "://" + stashUri.getHost() +	(stashUri.getPort() != -1 ? ":" + stashUri.getPort() : "");
        this.apiBaseUrl = this.host + "/rest/api/1.0/projects/";
    }
    
    public String getHost() {
		return host;
	}

    public String getProject() {
		return project;
	}

	public String getRepositoryName() {
		return repositoryName;
	}

	public List<StashPullRequestResponseValue> getPullRequests() {
        String response = getRequest(pullRequestsPath());
        try {
            return parsePullRequestJson(response).getPrValues();
        } catch(Exception e) {
            logger.log(Level.WARNING, "invalid pull request response.", e);
        }
        return Collections.emptyList();
    }

    public List<StashPullRequestComment> getPullRequestComments(String projectCode, String commentRepositoryName, String pullRequestId) {

        try {
            boolean isLastPage = false;
            int start = 0;
            List<StashPullRequestActivityResponse> commentResponses = new ArrayList<StashPullRequestActivityResponse>();
            while (!isLastPage) {
                String response = getRequest(
                        apiBaseUrl + projectCode + "/repos/" + commentRepositoryName + "/pull-requests/" + pullRequestId + "/activities?start=" + start);
                StashPullRequestActivityResponse resp = parseCommentJson(response);
                isLastPage = resp.getIsLastPage();
                if (!isLastPage) {
                    start = resp.getNextPageStart();
                }
                commentResponses.add(resp);
            }
            return extractComments(commentResponses);
        } catch(Exception e) {
            logger.log(Level.WARNING, "invalid pull request response.", e);
        }
        return Collections.emptyList();
    }

    public void deletePullRequestComment(String pullRequestId, String commentId) {
        String path = pullRequestPath(pullRequestId) + "/comments/" + commentId + "?version=0";
        deleteRequest(path);
    }


    public StashPullRequestComment postPullRequestComment(String pullRequestId, String comment) {
        String path = pullRequestPath(pullRequestId) + "/comments";
        try {
            String response = postRequest(path,  comment);
            return parseSingleCommentJson(response);

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public StashPullRequestMergableResponse getPullRequestMergeStatus(String pullRequestId) {
        String path = pullRequestPath(pullRequestId) + "/merge";
        try {
            String response = getRequest(path);
            return parsePullRequestMergeStatus(response);

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private HttpClient getHttpClient() {
        HttpClient client = new HttpClient();
        if (credentials != null) 
        	client.getState().setCredentials(AuthScope.ANY, credentials);
        
//        if (Jenkins.getInstance() != null) {
//            ProxyConfiguration proxy = Jenkins.getInstance().proxy;
//            if (proxy != null) {
//                logger.info("Jenkins proxy: " + proxy.name + ":" + proxy.port);
//                client.getHostConfiguration().setProxy(proxy.name, proxy.port);
//                String username = proxy.getUserName();
//                String password = proxy.getPassword();
//                // Consider it to be passed if username specified. Sufficient?
//                if (username != null && !"".equals(username.trim())) {
//                    logger.info("Using proxy authentication (user=" + username + ")");
//                    client.getState().setProxyCredentials(AuthScope.ANY,
//                        new UsernamePasswordCredentials(username, password));
//                }
//            }
//        }
        return client;
    }

    private String getRequest(String path) {
        logger.log(Level.FINEST, "PR-GET-REQUEST:" + path);
        HttpClient client = getHttpClient();
        GetMethod httpget = new GetMethod(path);
        client.getParams().setAuthenticationPreemptive(true);
        String response = null;
        try {
            client.executeMethod(httpget);
            response = httpget.getResponseBodyAsString();
        } catch (HttpException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.log(Level.FINEST, "PR-GET-RESPONSE:" + response);
        return response;
    }

    public void deleteRequest(String path) {
        HttpClient client = getHttpClient();
        DeleteMethod httppost = new DeleteMethod(path);
        client.getParams().setAuthenticationPreemptive(true);
        int res = -1;
        try {
            res = client.executeMethod(httppost);
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.log(Level.FINE, "Delete comment {" + path + "} returned result code; " + res);
    }

    private String postRequest(String path, String comment) throws UnsupportedEncodingException {
        logger.log(Level.FINEST, "PR-POST-REQUEST:" + path + " with: " + comment);
        HttpClient client = getHttpClient();
        PostMethod httppost = new PostMethod(path);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.getNodeFactory().objectNode();
        node.put("text", comment);

        StringRequestEntity requestEntity = null;
        try {
            requestEntity = new StringRequestEntity(
                    mapper.writeValueAsString(node),
                    "application/json",
                    "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }

        httppost.setRequestEntity(requestEntity);
        client.getParams().setAuthenticationPreemptive(true);
        String response = "";
        try {
            client.executeMethod(httppost);
            response = httppost.getResponseBodyAsString();
            logger.info("API Request Response: " + response);
        } catch (HttpException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.log(Level.FINEST, "PR-POST-RESPONSE:" + response);
        return response;

    }

    private StashPullRequestResponse parsePullRequestJson(String response) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        StashPullRequestResponse parsedResponse;
        parsedResponse = mapper.readValue(response, StashPullRequestResponse.class);
        return parsedResponse;
    }

    private StashPullRequestActivityResponse parseCommentJson(String response) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        StashPullRequestActivityResponse parsedResponse;
        parsedResponse = mapper.readValue(response, StashPullRequestActivityResponse.class);
        return parsedResponse;
    }

    private List<StashPullRequestComment> extractComments(List<StashPullRequestActivityResponse> responses) {
        List<StashPullRequestComment> comments = new ArrayList<StashPullRequestComment>();
        for (StashPullRequestActivityResponse parsedResponse: responses) {
            for (StashPullRequestActivity a : parsedResponse.getPrValues()) {
                if (a != null && a.getComment() != null) comments.add(a.getComment());
            }
        }
        return comments;
    }

    private StashPullRequestComment parseSingleCommentJson(String response) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        StashPullRequestComment parsedResponse;
        parsedResponse = mapper.readValue(
                response,
                StashPullRequestComment.class);
        return parsedResponse;
    }

    protected static StashPullRequestMergableResponse parsePullRequestMergeStatus(String response) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        StashPullRequestMergableResponse parsedResponse;
        parsedResponse = mapper.readValue(
                response,
                StashPullRequestMergableResponse.class);
        return parsedResponse;
    }

    private String pullRequestsPath() {
        return apiBaseUrl + this.project + "/repos/" + this.repositoryName + "/pull-requests/";
    }

    private String pullRequestPath(String pullRequestId) {
        return pullRequestsPath() + pullRequestId;
    }
}

