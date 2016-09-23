package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpClientError;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.params.HttpParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.params.CoreConnectionPNames;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by Nathan McCarthy
 */
@SuppressFBWarnings("EQ_DOESNT_OVERRIDE_EQUALS")
public class StashApiClient {

	private static final int HTTP_REQUEST_TIMEOUT_SECONDS = 60;
	private static final int HTTP_CONNECTION_TIMEOUT_SECONDS = 15;
	private static final int HTTP_SOCKET_TIMEOUT_SECONDS = 15;

    private static final Logger logger = Logger.getLogger(StashApiClient.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();

    private String apiBaseUrl;

    private String project;
    private String repositoryName;
    private Credentials credentials;


    public StashApiClient(String stashHost, String username, String password, String project, String repositoryName, boolean ignoreSsl) {
        this.credentials = new UsernamePasswordCredentials(username, password);
        this.project = project;
        this.repositoryName = repositoryName;
        this.apiBaseUrl = stashHost.replaceAll("/$", "") + "/rest/api/1.0/projects/";
        if (ignoreSsl) {
            Protocol easyhttps = new Protocol("https", (ProtocolSocketFactory) new EasySSLProtocolSocketFactory(), 443);
            Protocol.registerProtocol("https", easyhttps);
        }
    }

    public List<StashPullRequestResponseValue> getPullRequests() {
        List<StashPullRequestResponseValue> pullRequestResponseValues = new ArrayList<StashPullRequestResponseValue>();
        try {
            boolean isLastPage = false;
            int start = 0;
            while (!isLastPage) {
                String response = getRequest(pullRequestsPath(start));
                StashPullRequestResponse parsedResponse = parsePullRequestJson(response);
                isLastPage = parsedResponse.getIsLastPage();
                if (!isLastPage) {
                    start = parsedResponse.getNextPageStart();
                }
                pullRequestResponseValues.addAll(parsedResponse.getPrValues());
            }
            return pullRequestResponseValues;
        } catch (IOException e) {
            logger.log(Level.WARNING, "invalid pull request response.", e);
        }
        return Collections.EMPTY_LIST;
    }

    public List<StashPullRequestComment> getPullRequestComments(String projectCode, String commentRepositoryName,
                                                                String pullRequestId) {

        try {
            boolean isLastPage = false;
            int start = 0;
            List<StashPullRequestActivityResponse> commentResponses = new ArrayList<StashPullRequestActivityResponse>();
            while (!isLastPage) {
                String response = getRequest(
                        apiBaseUrl + projectCode + "/repos/" + commentRepositoryName + "/pull-requests/" +
                                pullRequestId + "/activities?start=" + start);
                StashPullRequestActivityResponse resp = parseCommentJson(response);
                isLastPage = resp.getIsLastPage();
                if (!isLastPage) {
                    start = resp.getNextPageStart();
                }
                commentResponses.add(resp);
            }
            return extractComments(commentResponses);
        } catch (Exception e) {
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
            String response = postRequest(path, comment);
            return parseSingleCommentJson(response);

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to post Stash PR comment " + path + " " + e);
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
            logger.log(Level.WARNING, "Failed to get Stash PR Merge Status " + path + " " + e);
        }
        return null;
    }

    public boolean mergePullRequest(String pullRequestId, String version) {
        String path = pullRequestPath(pullRequestId) + "/merge?version=" + version;
        try {
            String response = postRequest(path, null);
            return !response.equals(Integer.toString(HttpStatus.SC_CONFLICT));

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to merge Stash PR " + path + " " + e);
        }
        return false;
    }

    private HttpClient getHttpClient() {
        HttpClient client = new HttpClient();
    	HttpParams httpParams = client.getParams();
        //ConnectionTimeout : This denotes the time elapsed before the connection established or Server responded to connection request.
    	httpParams.setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, StashApiClient.HTTP_CONNECTION_TIMEOUT_SECONDS * 1000);
    	//SoTimeout : Maximum period inactivity between two consecutive data packets arriving at client side after connection is established.
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
        //http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html; section 14.10.
        //tells the server that we want it to close the connection when it has sent the response.
        //address large amount of close_wait sockets client and fin sockets server side
        httpget.setRequestHeader("Connection", "close");

        client.getParams().setAuthenticationPreemptive(true);
        String response = null;
        FutureTask<String> httpTask = null;
        Thread thread;
        try {
            //Run the http request in a future task so we have the opportunity
        	//to cancel it if it gets hung up; which is possible if stuck at
        	//socket native layer.  see issue JENKINS-30558
        	httpTask = new FutureTask<String>(new Callable<String>() {

            	private HttpClient client;
            	private GetMethod httpget;

                @Override
                public String call() throws Exception {
            		String response = null;
            		int responseCode = HttpStatus.SC_INTERNAL_SERVER_ERROR;
                    responseCode = client.executeMethod(httpget);
                    if (!validResponseCode(responseCode)) {
                        logger.log(Level.SEVERE, "Failing to get response from Stash PR GET" + httpget.getPath());
                        throw new RuntimeException("Didn't get a 200 response from Stash PR GET! Response; '" +
                                HttpStatus.getStatusText(responseCode) + "' with message; " + response);
                    }
                    InputStream responseBodyAsStream = httpget.getResponseBodyAsStream();
                    StringWriter stringWriter = new StringWriter();
                    IOUtils.copy(responseBodyAsStream, stringWriter, "UTF-8");
                    response = stringWriter.toString();

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

        } catch (TimeoutException e) {
            e.printStackTrace();
            httpget.abort();
            throw new RuntimeException(e);
        } catch (Exception e) {
        	e.printStackTrace();
        	throw new RuntimeException(e);
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
        //http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html; section 14.10.
        //tells the server that we want it to close the connection when it has sent the response.
        //address large amount of close_wait sockets client and fin sockets server side
        httppost.setRequestHeader("Connection", "close");

        client.getParams().setAuthenticationPreemptive(true);
        int res = -1;
        FutureTask<Integer> httpTask = null;
        Thread thread;

        try {
          //Run the http request in a future task so we have the opportunity
        	//to cancel it if it gets hung up; which is possible if stuck at
        	//socket native layer.  see issue JENKINS-30558
        	httpTask = new FutureTask<Integer>(new Callable<Integer>() {

            	private HttpClient client;
            	private DeleteMethod httppost;

                @Override
                public Integer call() throws Exception {
                	int res = -1;
                	res = client.executeMethod(httppost);
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

        } catch (TimeoutException e) {
            e.printStackTrace();
            httppost.abort();
            throw new RuntimeException(e);
        } catch (Exception e) {
        	e.printStackTrace();
        	throw new RuntimeException(e);
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
        //http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html; section 14.10.
        //tells the server that we want it to close the connection when it has sent the response.
        //address large amount of close_wait sockets client and fin sockets server side
        httppost.setRequestHeader("Connection", "close");
        httppost.setRequestHeader("X-Atlassian-Token", "no-check"); //xsrf

        if(comment != null) {
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
        }

        client.getParams().setAuthenticationPreemptive(true);
        String response = "";
        FutureTask<String> httpTask = null;
        Thread thread;

        try {
          //Run the http request in a future task so we have the opportunity
        	//to cancel it if it gets hung up; which is possible if stuck at
        	//socket native layer.  see issue JENKINS-30558
            httpTask = new FutureTask<String>(new Callable<String>() {

            	private HttpClient client;
            	private PostMethod httppost;

                @Override
                public String call() throws Exception {
                	String response = "";
                	int responseCode = HttpStatus.SC_INTERNAL_SERVER_ERROR;

                    responseCode = client.executeMethod(httppost);
                    if (!validResponseCode(responseCode)) {
                        logger.log(Level.SEVERE, "Failing to get response from Stash PR POST" + httppost.getPath());
                        throw new RuntimeException("Didn't get a 200 response from Stash PR POST! Response; '" +
                                HttpStatus.getStatusText(responseCode) + "' with message; " + response);
                    }
                    InputStream responseBodyAsStream = httppost.getResponseBodyAsStream();
                    StringWriter stringWriter = new StringWriter();
                    IOUtils.copy(responseBodyAsStream, stringWriter, "UTF-8");
                    response = stringWriter.toString();
                    logger.log(Level.FINEST, "API Request Response: " + response);

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

        } catch (TimeoutException e) {
            e.printStackTrace();
            httppost.abort();
            throw new RuntimeException(e);
        } catch (Exception e) {
           e.printStackTrace();
           throw new RuntimeException(e);
        } finally {
        	  httppost.releaseConnection();
        }

        logger.log(Level.FINEST, "PR-POST-RESPONSE:" + response);

        return response;
    }

    private boolean validResponseCode(int responseCode) {
        return responseCode == HttpStatus.SC_OK ||
                responseCode == HttpStatus.SC_ACCEPTED ||
                responseCode == HttpStatus.SC_CREATED ||
                responseCode == HttpStatus.SC_NO_CONTENT ||
                responseCode == HttpStatus.SC_RESET_CONTENT;
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

    private String pullRequestsPath(int start) {
        String basePath = pullRequestsPath();
        return basePath.substring(0, basePath.length() - 1) + "?start=" + start;
    }

    private static class EasySSLProtocolSocketFactory extends org.apache.commons.httpclient.contrib.ssl.EasySSLProtocolSocketFactory {
        private static final Log LOG = LogFactory.getLog(EasySSLProtocolSocketFactory.class);
        private SSLContext sslcontext = null;

        private static SSLContext createEasySSLContext() {
            try {
                TrustManager[] trustAllCerts = new TrustManager[]{
                        new X509TrustManager(){
                            public X509Certificate[] getAcceptedIssuers(){ return null; }
                            public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                            public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                        }
                };

                SSLContext context = SSLContext.getInstance("SSL");
                context.init(
                        null,
                        trustAllCerts,
                        null);
                return context;
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                throw new HttpClientError(e.toString());
            }
        }

        private SSLContext getSSLContext() {
            if (this.sslcontext == null) {
                this.sslcontext = createEasySSLContext();
            }
            return this.sslcontext;
        }

        public Socket createSocket(String host, int port, InetAddress clientHost, int clientPort) throws IOException, UnknownHostException {
            return this.getSSLContext().getSocketFactory().createSocket(host, port, clientHost, clientPort);
        }

        public Socket createSocket(String host, int port, InetAddress localAddress, int localPort, HttpConnectionParams params) throws IOException, UnknownHostException, ConnectTimeoutException {
            if(params == null) {
                throw new IllegalArgumentException("Parameters may not be null");
            } else {
                int timeout = params.getConnectionTimeout();
                SSLSocketFactory socketfactory = this.getSSLContext().getSocketFactory();
                if(timeout == 0) {
                    return socketfactory.createSocket(host, port, localAddress, localPort);
                } else {
                    Socket socket = socketfactory.createSocket();
                    InetSocketAddress localaddr = new InetSocketAddress(localAddress, localPort);
                    InetSocketAddress remoteaddr = new InetSocketAddress(host, port);
                    socket.bind(localaddr);
                    socket.connect(remoteaddr, timeout);
                    return socket;
                }
            }
        }

        public Socket createSocket(String host, int port) throws IOException {
            return this.getSSLContext().getSocketFactory().createSocket(host, port);
        }

        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            return this.getSSLContext().getSocketFactory().createSocket(socket, host, port, autoClose);
        }
    }
}

