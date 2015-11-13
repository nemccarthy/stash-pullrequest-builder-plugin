package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.jgit.transport.URIish;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by Nathan McCarthy
 */
public class StashApiClient {
    private static final Logger logger = Logger.getLogger(StashApiClient.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String apiBaseUrl;
    private final String host;
    private final String project;
    private final String repositoryName;
    private final Credentials credentials;

    public StashApiClient(URIish stashUri, StandardUsernamePasswordCredentials credentials) {
        this(null, stashUri, credentials == null ? null : new UsernamePasswordCredentials(
        		credentials.getUsername(), 
        		credentials.getPassword().getPlainText()));
    }
    
    public StashApiClient(String host, URIish stashUri, UsernamePasswordCredentials credentials) {
    	// validate stash uri
    	if (stashUri == null || !stashUri.getPath().startsWith("/scm/")) {
        	throw new IllegalArgumentException("Invalid stash URI " + stashUri);
        }
    	
        this.credentials = credentials;
        // split on / after removing prefix /scm/ should give the project name as first entry       
        this.project = stashUri.getPath().substring(5).split("/")[0];
        this.repositoryName = stashUri.getHumanishName();
        // override host if provided
        this.host = host != null ? host : stashUri.getScheme() + "://" + stashUri.getHost() +	(stashUri.getPort() != -1 ? ":" + stashUri.getPort() : "");
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
        return Collections.emptyList();
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
        return Collections.emptyList();
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
        int responseCode;
        try {
            responseCode = client.executeMethod(httpget);
            InputStream responseBodyAsStream = httpget.getResponseBodyAsStream();
            StringWriter stringWriter = new StringWriter();
            IOUtils.copy(responseBodyAsStream, stringWriter, "UTF-8");
            response = stringWriter.toString();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to process PR get request; " + path, e);
        }
        logger.log(Level.FINEST, "PR-GET-RESPONSE:" + response);
        if (!validResponseCode(responseCode)) {
            logger.log(Level.SEVERE, "Failing to get response from Stash PR GET" + path);
            throw new RuntimeException("Didn't get a 200 response from Stash PR GET! Response; '" +
                    HttpStatus.getStatusText(responseCode) + "' with message; " + response);
        }
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
        int responseCode;
        try {
            responseCode = client.executeMethod(httppost);
            InputStream responseBodyAsStream = httppost.getResponseBodyAsStream();
            StringWriter stringWriter = new StringWriter();
            IOUtils.copy(responseBodyAsStream, stringWriter, "UTF-8");
            response = stringWriter.toString();
            logger.info("API Request Response: " + response);
        }  catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to process PR get request; " + path, e);
        }
        logger.log(Level.FINEST, "PR-POST-RESPONSE:" + response);
        if (!validResponseCode(responseCode)) {
            logger.log(Level.SEVERE, "Failing to get response from Stash PR POST" + path);
            throw new RuntimeException("Didn't get a 200 response from Stash PR POST! Response; '" +
                    HttpStatus.getStatusText(responseCode) + "' with message; " + response);
        }
        return response;
    }

    private boolean validResponseCode(int responseCode) {
        return responseCode == HttpStatus.SC_OK ||
                responseCode == HttpStatus.SC_ACCEPTED ||
                responseCode == HttpStatus.SC_CREATED ||
                responseCode == HttpStatus.SC_NO_CONTENT ||
                responseCode == HttpStatus.SC_RESET_CONTENT ;
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

