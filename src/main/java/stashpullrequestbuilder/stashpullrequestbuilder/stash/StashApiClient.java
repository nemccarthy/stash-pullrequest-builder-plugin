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
import org.apache.commons.httpclient.params.HttpParams;
import org.apache.commons.io.IOUtils;
import org.apache.http.params.CoreConnectionPNames;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by Nathan McCarthy
 */
public class StashApiClient {
	
	private static final int HTTP_REQUEST_TIMEOUT_SECONDS = 30;
	private static final int HTTP_CONNECTION_TIMEOUT_SECONDS = 10;
	private static final int HTTP_SOCKET_TIMEOUT_SECONDS = 10;
		
    private static final Logger logger = Logger.getLogger(StashApiClient.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();

    private String apiBaseUrl ;

    private String project;
    private String repositoryName;
    private Credentials credentials;


    public StashApiClient(String stashHost, String username, String password, String project, String repositoryName) {
        this.credentials = new UsernamePasswordCredentials(username, password);
        this.project = project;
        this.repositoryName = repositoryName;
        this.apiBaseUrl = stashHost.replaceAll("/$", "") + "/rest/api/1.0/projects/";
    }

    public List<StashPullRequestResponseValue> getPullRequests() {
        String response = getRequest(pullRequestsPath());
        try {
            return parsePullRequestJson(response).getPrValues();
        } catch(Exception e) {
            logger.log(Level.WARNING, "invalid pull request response.", e);
        }
        return Collections.EMPTY_LIST;
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
        return Collections.EMPTY_LIST;
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
    	HttpParams httpParams = client.getParams();    	
    	httpParams.setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, StashApiClient.HTTP_CONNECTION_TIMEOUT_SECONDS * 1000);
    	httpParams.setParameter(CoreConnectionPNames.SO_TIMEOUT, StashApiClient.HTTP_SOCKET_TIMEOUT_SECONDS * 1000);
        
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
        client.getState().setCredentials(AuthScope.ANY, credentials);
        GetMethod httpget = new GetMethod(path);
        httpget.setRequestHeader("Connection", "close");
        client.getParams().setAuthenticationPreemptive(true);
        String response = null;
        FutureTask<String> httpTask = null;
        Thread thread;
        try {
            httpTask = new FutureTask<String>(new Callable<String>() {

            	private HttpClient client;
            	private GetMethod httpget;
            	
                @Override
                public String call() throws Exception {
            		String response = null;
                	try {
                        client.executeMethod(httpget);
                        InputStream responseBodyAsStream = httpget.getResponseBodyAsStream();
                        StringWriter stringWriter = new StringWriter();
                        IOUtils.copy(responseBodyAsStream, stringWriter, "UTF-8");
                        response = stringWriter.toString();
                    } catch (HttpException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                	
                	return response;

                }
                
                public Callable<String> init(HttpClient client, GetMethod httpget) {
                	this.client = client;
                	this.httpget = httpget;
                	return this;
                }
                
              }.init(client, httpget));
            thread = new Thread(httpTask);
            thread.start();
            response = httpTask.get((long)StashApiClient.HTTP_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
            httpget.abort();
            httpTask.cancel(true);
        } finally {
        	httpget.releaseConnection();
        }
            
        logger.log(Level.FINEST, "PR-GET-RESPONSE:" + response);
        return response;
    }

    public void deleteRequest(String path) {
        HttpClient client = getHttpClient();
        client.getState().setCredentials(AuthScope.ANY, credentials);
        DeleteMethod httppost = new DeleteMethod(path);
        httppost.setRequestHeader("Connection", "close");
        client.getParams().setAuthenticationPreemptive(true);
        int res = -1;
        FutureTask<Integer> httpTask = null;
        Thread thread;

        try {
            httpTask = new FutureTask<Integer>(new Callable<Integer>() {

            	private HttpClient client;
            	private DeleteMethod httppost;
            	
                @Override
                public Integer call() throws Exception {
                	int res = -1;
                    try {
                        res = client.executeMethod(httppost);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    
                    return res;

                }
                
                public Callable<Integer> init(HttpClient client, DeleteMethod httppost) {
                	this.client = client;
                	this.httppost = httppost;
                	return this;
                }
                
              }.init(client, httppost));
            thread = new Thread(httpTask);
            thread.start();
            res = httpTask.get((long)StashApiClient.HTTP_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
            httppost.abort();
            httpTask.cancel(true);
        } finally {
        	httppost.releaseConnection();
        }
        
        logger.log(Level.FINE, "Delete comment {" + path + "} returned result code; " + res);
    }

    private String postRequest(String path, String comment) throws UnsupportedEncodingException {
        logger.log(Level.FINEST, "PR-POST-REQUEST:" + path + " with: " + comment);
        HttpClient client = getHttpClient();
        client.getState().setCredentials(AuthScope.ANY, credentials);
        PostMethod httppost = new PostMethod(path);
        httppost.setRequestHeader("Connection", "close");

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
        FutureTask<String> httpTask = null;
        Thread thread;

        try {
            httpTask = new FutureTask<String>(new Callable<String>() {

            	private HttpClient client;
            	private PostMethod httppost;
            	
                @Override
                public String call() throws Exception {
                	String response = "";
                	
                    try {
                        client.executeMethod(httppost);
                        InputStream responseBodyAsStream = httppost.getResponseBodyAsStream();
                        StringWriter stringWriter = new StringWriter();
                        IOUtils.copy(responseBodyAsStream, stringWriter, "UTF-8");
                        response = stringWriter.toString();
                        logger.info("API Request Response: " + response);
                    } catch (HttpException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    
                    return response;

                }
                
                public Callable<String> init(HttpClient client, PostMethod httppost) {
                	this.client = client;
                	this.httppost = httppost;
                	return this;
                }
                
              }.init(client, httppost));
            thread = new Thread(httpTask);
            thread.start();
            response = httpTask.get((long)StashApiClient.HTTP_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
            httppost.abort();
            httpTask.cancel(true);            
        } finally {
        	httppost.releaseConnection();
        }
                
        logger.log(Level.FINEST, "PR-POST-RESPONSE:" + response);
        return response;
        

    }

    private StashPullRequestResponse parsePullRequestJson(String response) throws IOException {
        StashPullRequestResponse parsedResponse;
        parsedResponse = mapper.readValue(response, StashPullRequestResponse.class);
        return parsedResponse;
    }

    private StashPullRequestActivityResponse parseCommentJson(String response) throws IOException {
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
        StashPullRequestComment parsedResponse;
        parsedResponse = mapper.readValue(
                response,
                StashPullRequestComment.class);
        return parsedResponse;
    }

    protected static StashPullRequestMergableResponse parsePullRequestMergeStatus(String response) throws IOException {
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

