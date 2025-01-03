package gov.nasa.jpl.labcas.data_access_api.service;

import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Logger;

// Download audit track
import gov.nasa.jpl.labcas.data_access_api.filter.AuthenticationFilter;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

import gov.nasa.jpl.labcas.data_access_api.aws.AwsS3DownloadHelper;
import gov.nasa.jpl.labcas.data_access_api.aws.AwsUtils;
import gov.nasa.jpl.labcas.data_access_api.utils.DownloadHelper;

import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;


@Path("/")
@Produces(MediaType.APPLICATION_OCTET_STREAM)
public class DownloadServiceImpl extends SolrProxy implements DownloadService  {
	
	private final static Logger LOG = Logger.getLogger(DownloadServiceImpl.class.getName());

	// Download audit track
	private static final SimpleDateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
	
	// class that generates temporary URLs for S3 downloads
	AwsS3DownloadHelper awsS3DownloadHelper;
	
	public DownloadServiceImpl() throws Exception {
		
		super();
		
		awsS3DownloadHelper = new AwsS3DownloadHelper();
		
	}

	@Override
	@GET
	@Path("/download")
	public Response download(@Context HttpServletRequest httpRequest, @Context ContainerRequestContext requestContext, @QueryParam("id") String id) {
		LOG.info("📯 HEYO! I am in the download part");

		// note: @QueryParam('id') automatically URL-decodes the 'id' value
		if (id==null) {
			return Response.status(Status.BAD_REQUEST).entity("Missing mandatory parameter 'id'").build();
		} else if (!isSafe(id)) {
			return Response.status(Status.BAD_REQUEST).entity(UNSAFE_CHARACTERS_MESSAGE).build();
		}

		try {
			
			String fileLocation = null;
			String realFileName = null;
			String fileName = null;
			String filePath = null;
			String name = null;
			
			// query Solr for file with that specific id
			SolrQuery request = new SolrQuery();
			request.setQuery("id:\""+id+"\"");
			LOG.info("🆔 HEYO! The id is «" + id + "»");
			
			// add access control
			String acfq = getAccessControlQueryStringValue(requestContext);
			if (!acfq.isEmpty()) {
				request.setFilterQueries(acfq);
			}
			
			// return file location on file system or S3 + file name
			request.setFields( new String[] { SOLR_FIELD_FILE_LOCATION, SOLR_FIELD_FILE_NAME, SOLR_FIELD_NAME } );
			
			// note: SolrJ will URL-encode the HTTP GET parameter values
			LOG.info("Executing Solr request to 'files' core: "+request.toString());
			QueryResponse response = solrServers.get(SOLR_CORE_FILES).query(request);
			
			SolrDocumentList docs = response.getResults();
			Iterator<SolrDocument> iter = docs.iterator();
			while (iter.hasNext()) {
				SolrDocument doc = iter.next();
				LOG.info(doc.toString());
				LOG.info("=== 1 about to get fileLocation");
				fileLocation = (String)doc.getFieldValue(SOLR_FIELD_FILE_LOCATION);
				LOG.info("=== 2 got fileLocation = «" + fileLocation + "»");
				fileName = (String)doc.getFieldValue(SOLR_FIELD_FILE_NAME);
				realFileName = (String)doc.getFieldValue(SOLR_FIELD_FILE_NAME);
				LOG.info("=== 3 got fileName = «" + fileName + "»");
				if (doc.getFieldValuesMap().containsKey(SOLR_FIELD_NAME)) {
					LOG.info("=== 3½ ok");
					Object nameFieldValue = doc.getFieldValue(SOLR_FIELD_NAME);
					if (nameFieldValue != null) {
						ArrayList asList = (ArrayList) nameFieldValue;
						if (asList.size() > 0) {
							String firstNameField = (String) asList.get(0);
							if (firstNameField != null && firstNameField.length() > 0) {
								LOG.info("=== 4 name field value «" + firstNameField + "» overriding fileName «" + fileName + "»");
								realFileName = firstNameField;
							}
						}
					}
				}
				filePath = fileLocation + "/" + realFileName;
				LOG.info("=== 6 filePath is «" + filePath + "»");
				LOG.info("File path="+filePath.toString());
				
				//return Response.status(Status.OK).entity(filePath.toString()).build();
				
			}
						
			if (filePath!=null) {

				LOG.info("Using fileLocation="+fileLocation);
				
				if (fileLocation.startsWith("s3")) {
					
					// generate temporary URL and redirect client
					String key = AwsUtils.getS3key(filePath);
					LOG.info("Using s3key="+key);
					URL url = awsS3DownloadHelper.getUrl(key, null); // versionId=null
					LOG.info("Redirecting client to S3 URL:"+url.toString());
					return Response.temporaryRedirect(url.toURI()).build();
					
				} else {
					// Download audit track
					try {
						String dn = (String) requestContext.getProperty(AuthenticationFilter.USER_DN);
						String now = iso8601.format(new Date());
						File downloadLog = new File(System.getenv("LABCAS_HOME"), "download.log");
						// TODO: rotation? Or just use Java Logging?
						PrintWriter writer = null;
						try {
							// true to FileWriter means append
							writer = new PrintWriter(new BufferedWriter(new FileWriter(downloadLog, true)));
							writer.println(now + ";" + dn + ";" + id);
						} finally {
							if (writer != null) writer.close();
						}
					} catch (IOException ex) {
						LOG.warning("Could not log this download (" + ex.getClass().getName() + ") but continuing");
						ex.printStackTrace();
						LOG.warning(ex.getMessage());
						LOG.warning("Now continuing to download the file with the download helper…");
					}

					// read file from local file system and stream it to client
					DownloadHelper dh = new DownloadHelper(Paths.get(filePath));
			        return Response
			                .ok(dh, MediaType.APPLICATION_OCTET_STREAM)
			                // "content-disposition" header instructs the client to keep the same file name
			                .header("content-disposition","attachment; filename=\"" + fileName + "\"")
			                .build();
				}
	        
			} else {
				return Response.status(Status.NOT_FOUND).entity("File not found or not authorized").build();
			}	
		} catch (RuntimeException e) {
			LOG.info("=== RUNTIME EXCEPTION " + e.getClass().getName());
			throw e;
		} catch (Exception e) {
			// send 500 "Internal Server Error" response
			LOG.warning("💥 HEYO … nope! We got an exception of type «" + e.getClass().getName() + "»");
			e.printStackTrace();
			LOG.warning(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		}
	}


	private static String initiateZIP(String email, String query) throws IOException {
		LOG.info("👀 initiateZIP for " + email + " and query " + query);
		// Should we make this a parameter?
		String urlString = String.format(
			"https://edrn-docker/zipperlab/edrn?operation=initiate&email=%s&query=%s",
			URLEncoder.encode(email, "UTF-8"),
			URLEncoder.encode(query, "UTF-8")
		);
		LOG.info("👀 URL string formatted: " + urlString);
		URL url = new URL(urlString);
		LOG.info("👀 calling URL " + url);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
			LOG.info("👀 got OK status, so reading the UUID");
			BufferedReader in = null;
			try {
				in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				String uuid = in.readLine();
				LOG.info("👀 Got UUID " + uuid);
				return uuid;
			} finally {
				if (in != null) in.close();
			}
		} else {
			LOG.info("🚨 Got HTTP status " + connection.getResponseCode());
			throw new IOException("Request failed with HTTP status " + connection.getResponseCode());
		}
	}


	@Override
	@GET
	@Path("/zip")
	@Produces("text/plain")
	public Response zip(
		@Context HttpServletRequest httpRequest,
		@Context ContainerRequestContext requestContext,
		@QueryParam("email") String email,
		@QueryParam("query") String query
	) {
		LOG.info("👀 I see you, " + email + ", with your zip request for " + query);
		try {
			LOG.info("👀 getting uuid");
			String uuid = initiateZIP(email, query);
			LOG.info("👀 uuid is " + uuid);
			return Response.status(Status.OK).entity("Your UIID is: " + uuid + "\n").build();
		} catch (IOException ex) {
			LOG.warning("🚨🚨🚨 " + ex.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
		}
	}

	@Override
	@GET
	@Path("/ping")
	@Produces("text/plain")
	public Response ping(
		@Context HttpServletRequest httpRequest,
		@Context ContainerRequestContext requestContext,
		@QueryParam("message") String message
	) {
		LOG.info("📡 Message received: " + message);
		return Response.status(Status.OK).entity("🧾 Message received, " + message + "\n").build();
	}
}
