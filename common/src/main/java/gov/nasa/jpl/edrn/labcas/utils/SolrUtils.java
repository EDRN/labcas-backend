package gov.nasa.jpl.edrn.labcas.utils;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.oodt.cas.metadata.Metadata;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import gov.nasa.jpl.edrn.labcas.Constants;
import gov.nasa.jpl.edrn.labcas.XmlUtils;

/**
 * Class containing general utilities to query/update the Solr index behind an OODT File Manager.
 * 
 * @author luca
 *
 */
public class SolrUtils {
		
	private final static Logger LOG = Logger.getLogger(SolrUtils.class.getName());
	
	// default value for SOLR URL
	private static String SOLR_URL = "http://localhost:8080/solr/oodt-fm";
	
	// IMPORTANT: must re-use the same SolrServer instance across all requests to prevent memory leaks
	// see https://issues.apache.org/jira/browse/SOLR-861 
	// this method instantiates the shared instance of SolrServer
	private static SolrServer solrServer = null;
	static {
		if (System.getenv(Constants.ENV_SOLR_URL)!=null) {
			SOLR_URL = System.getenv(Constants.ENV_SOLR_URL);
			try {
				solrServer = new CommonsHttpSolrServer( SOLR_URL );
			} catch(MalformedURLException e) {
				e.printStackTrace();
				LOG.warning(e.getMessage());
			}
		}
	}

	/**
	 * Utility method to query Solr for all products of a given dataset and version.
	 * 
	 * @param datasetName
	 * @param datasetVersion
	 * @return
	 */
	public static List<String> queryAllProducts(String datasetName, int datasetVersion) {
		
		List<String> ids = new ArrayList<String>();
		
		// build Solr query
        SolrQuery request = new SolrQuery();
        request.setQuery("*:*");
        request.addFilterQuery("Dataset:"+datasetName,"Version:"+datasetVersion);
        request.setRows(Constants.MAX_SOLR_ROWS);
        
        // execute Solr query
        try {

	        QueryResponse response = solrServer.query( request );
	        SolrDocumentList docs = response.getResults();
	        Iterator<SolrDocument> iter = docs.iterator();
	        while (iter.hasNext()) {
	            SolrDocument doc = iter.next();
	            //LOG.fine(doc.toString());
	            String id = (String) doc.getFieldValue("id"); 
	            LOG.info("Retrieved Solr document id="+id);
	            ids.add(id);
	        }
	        
        } catch(Exception e) {
        	e.printStackTrace();
        	LOG.warning(e.getMessage()); // will return empty ids list
        }
		
		return ids;
		
	}
	
	/**
	 * Utility method to query Solr for a specific product belonging to a dataset and version.
	 * @param datasetName
	 * @param datasetVersion
	 * @param productName
	 * @return
	 */
	public static String queryProduct(String datasetName, int datasetVersion, String productName) {
		
		String id = null;
		
		// build Solr query
        SolrQuery request = new SolrQuery();
        request.setQuery("*:*");
        request.addFilterQuery(Constants.METADATA_KEY_DATASET+":"+datasetName, 
        		               Constants.METADATA_KEY_VERSION+":"+datasetVersion, 
        		               Constants.METADATA_KEY_PRODUCT_NAME+":"+productName);
        request.setRows(1);
        
        // execute Solr query
        try {

	        QueryResponse response = solrServer.query( request );
	        SolrDocumentList docs = response.getResults();
	        Iterator<SolrDocument> iter = docs.iterator();
	        while (iter.hasNext()) {
	            SolrDocument doc = iter.next();
	            //LOG.fine(doc.toString());
	            id = (String) doc.getFieldValue("id"); 
	            LOG.info("Retrieved Solr document id="+id);
	        }
	        
        } catch(Exception e) {
        	e.printStackTrace();
        	LOG.warning(e.getMessage());
        }
        
        return id;
		
	}
	
	/**
	 * Utility method to build a Solr XML update document 
	 * for all given records and all metadata fields.
	 * @param ids
	 * @param datasetMetadata
	 * @return
	 */
	public static String buildSolrXmlDocument(HashMap<String, Metadata> updateMetadataMap) throws Exception {
		
        // create Solr/XML update document
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = dbf.newDocumentBuilder();
        Document xmlDocument = builder.newDocument();
        
        // <add>
        Element addElement = xmlDocument.createElement("add");
        xmlDocument.appendChild(addElement);
        
        // loop over all records that must be updated
        for (String id : updateMetadataMap.keySet()) {
        
	        // <doc>
	        Element docElement = xmlDocument.createElement("doc");
	        addElement.appendChild(docElement);
	        
	        // <field name="id">38b6e7e6-3a9b-4565-9d57-37e8104b4fde</field>
	        Element fieldElement = xmlDocument.createElement("field");
	        fieldElement.setAttribute("name", "id");
	        fieldElement.insertBefore(xmlDocument.createTextNode(id), fieldElement.getLastChild());
	        docElement.appendChild(fieldElement);
	        
	         // to add one or more values:
	         // <field name="Institution" update="set">Darthmouth</field>
	         // <field name="Institution" update="set">Children Hospital</field>
	         // to remove a key:
	         // <field name="Institution" update="set" null="true"/>
	        Metadata metadata = updateMetadataMap.get(id);
			 for (String key : metadata.getAllKeys()) {
				for (String val : metadata.getAllMetadata(key)) {
					LOG.info("\t==> XML: Updating dataset metadata key=["+key+"] value=["+val+"]");
					
					Element metFieldElement = xmlDocument.createElement("field");
					metFieldElement.setAttribute("name", key);
					metFieldElement.setAttribute("update", "set");
					if (StringUtils.hasText(val)) {
						// add this value to that key
						metFieldElement.insertBefore(xmlDocument.createTextNode(val), metFieldElement.getLastChild());
					} else {
						// remove all values for that key
						metFieldElement.setAttribute("null", "true");
					}
			        docElement.appendChild(metFieldElement);
					
				}
			 }
        
        } // loop over record ids
			 
        String xmlString = XmlUtils.xmlToString(xmlDocument);
        LOG.info(xmlString);
        return xmlString;

	}
	
	/**
	 * Utility method to POST an XML document to Solr
	 * @param solrXmlDocument
	 */
	public static void postSolrXml(String solrXmlDocument) {
		
	    //String strURL = "http://edrn-frontend.jpl.nasa.gov:8080/solr/oodt-fm/update?commit=true";
	    String solrUpdateUrl = SOLR_URL + "/update?commit=true";
	
	    HttpClient client = new DefaultHttpClient();
	    HttpPost post = new HttpPost(solrUpdateUrl);
	    
	    try {
		    HttpEntity entity = new ByteArrayEntity(solrXmlDocument.getBytes("UTF-8"));
		    post.setEntity(entity);
		    post.setHeader("Content-Type", "application/xml");
		    HttpResponse response = client.execute(post);
		    String result = EntityUtils.toString(response.getEntity());
		    LOG.info("POST result="+result);
	   
	    } catch(Exception e) {
	    	LOG.warning(e.getMessage());
	    	
	    } finally {   
		    // must release connection
		    post.releaseConnection();
	    }
	    
	}

}
