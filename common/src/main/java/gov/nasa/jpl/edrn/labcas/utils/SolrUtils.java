package gov.nasa.jpl.edrn.labcas.utils;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.apache.oodt.cas.filemgr.repository.XMLRepositoryManager;
import org.apache.oodt.cas.filemgr.structs.ProductType;
import org.apache.oodt.cas.metadata.Metadata;
import org.apache.oodt.commons.io.DirectorySelector;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.jsoup.Jsoup;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import gov.nasa.jpl.edrn.labcas.Constants;

import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.conn.ssl.NoopHostnameVerifier;



/**
 * Class containing general utilities to query/update the Solr index.
 * 
 * @author luca
 *
 */
public class SolrUtils {
		
	private final static Logger LOG = Logger.getLogger(SolrUtils.class.getName());
	
	// default base Solr URL if $FILEMGR_URL is not set
	private static String SOLR_URL = "https://localhost:8984/solr"; 
	
	// list of OODT fields that are NOT transferred to the public Solr index
	private static Set<String> IGNORED_FIELDS = new HashSet<String>(
			Arrays.asList("WorkflowManagerUrl", "TaskId", "WorkflowInstId", "JobId",
				          "WorkflowId", "WorkflowName", "ProcessingNode",
				          Constants.METADATA_KEY_NEW_VERSION,
				          Constants.METADATA_KEY_UPDATE_COLLECTION,
				          Constants.METADATA_KEY_UPDATE_DATASET,
				          Constants.METADATA_KEY_UPLOAD_FILES,
				          Constants.METADATA_KEY_WORKFLOW_ORDER));
	
	// list of OODT fields that are transferred with no changes to Solr
	// note that fields of type File* are also passed through by default
	private static Set<String> PASS_THROUGH_FIELDS = new HashSet<String>( 
			Arrays.asList(Constants.METADATA_KEY_COLLECTION_NAME, 
					      Constants.METADATA_KEY_DATASET_NAME,
					      Constants.METADATA_KEY_DATASET_ID,
					      Constants.METADATA_KEY_DATASET_VERSION,
					      Constants.METADATA_KEY_OWNER_PRINCIPAL));

	private final static String SOLR_CORE_COLLECTIONS = "collections";
	private final static String SOLR_CORE_DATASETS = "datasets";
	private final static String SOLR_CORE_FILES = "files";
	private final static String SOLR_CORE_OODT = "oodt-fm";
	
	// IMPORTANT: must re-use the same SolrServer instance across all requests to prevent memory leaks
	// see https://issues.apache.org/jira/browse/SOLR-861 
	// this method instantiates the shared instances of SolrServer (one per core)
	private static Map<String, SolrServer> solrServers = new HashMap<String, SolrServer>();
	static {
		try {
			
			if (System.getenv("FILEMGR_URL")!=null) {
				SOLR_URL = System.getenv("FILEMGR_URL").replaceAll("9000", "8984")+"/solr";
				// remove possible '//'
				SOLR_URL = SOLR_URL.replaceAll("8984//", "8984/");
				SOLR_URL = SOLR_URL.replaceAll("http://", "https://");
			}
			LOG.info("Using base SOLR_URL="+SOLR_URL);

			// Since Solr is using a self-signed cert on https://localhost, we need to ignore cert errors:
			Protocol https = new Protocol("https", new InsecureSocketFactory(), 443);
			Protocol.registerProtocol("https", https);

			solrServers.put(SOLR_CORE_COLLECTIONS, new CommonsHttpSolrServer(SOLR_URL+"/"+SOLR_CORE_COLLECTIONS) );
			solrServers.put(SOLR_CORE_DATASETS, new CommonsHttpSolrServer(SOLR_URL+"/"+SOLR_CORE_DATASETS) );
			solrServers.put(SOLR_CORE_FILES, new CommonsHttpSolrServer(SOLR_URL+"/"+SOLR_CORE_FILES) );
			solrServers.put(SOLR_CORE_OODT, new CommonsHttpSolrServer(SOLR_URL+"/"+SOLR_CORE_OODT) );
			
		} catch (RuntimeException ex) {
			throw ex;
		} catch (Exception ex) {
			ex.printStackTrace();
			LOG.warning(ex.getMessage());
			System.exit(-42);
		}
	}

	/**
	 * Utility method to query Solr for all products of a given dataset and version.
	 * 
	 * @param datasetName
	 * @param datasetVersion
	 * @return
	 */
	public static List<String> queryAllProducts(String datasetId, String datasetVersion) {
		
		List<String> ids = new ArrayList<String>();
		
		// build Solr query
        SolrQuery request = new SolrQuery();
        request.setQuery("*:*");
        request.addFilterQuery(Constants.METADATA_KEY_DATASET_ID+":"+datasetId,
        		               Constants.METADATA_KEY_DATASET_VERSION+":"+datasetVersion);
        request.setRows(Constants.MAX_SOLR_ROWS);
        LOG.fine("Executing Solr query: "+request.toString());
        
        // execute Solr query
        try {

	        QueryResponse response = solrServers.get(SOLR_CORE_FILES).query( request );
	        SolrDocumentList docs = response.getResults();
	        Iterator<SolrDocument> iter = docs.iterator();
	        while (iter.hasNext()) {
	            SolrDocument doc = iter.next();
	            //LOG.fine(doc.toString());
	            String id = (String) doc.getFieldValue("id"); 
	            LOG.fine("Retrieved Solr document id="+id);
	            ids.add(id);
	        }
	        
        } catch(Exception e) {
        	e.printStackTrace();
        	LOG.warning(e.getMessage()); // will return empty ids list
        }
		
		return ids;
		
	}
	
	/**
	 * Queries the OODT Solr catalog for the id of the product that matches the given metadata fields.
	 * Returns null if no matching product is found.
	 * 
	 * @param metadata
	 * @return
	 */
	public static String queryProductId(Metadata metadata) {
		
		String productId = null;
		
		try {
		
			// build Solr query
	        SolrQuery request = new SolrQuery();
	        request.setQuery("*:*");
	        // query product by final archive location
	        // note: appearently OODT encodes 'some' characters but not others in the filename:
	        // must try to execute the exact same substitutions to have the Solr query succeds...
	        String fileUri = "file:" + metadata.getMetadata(Constants.METADATA_KEY_FILE_PATH) 
	                       + "/" + myencoding(metadata.getMetadata(Constants.METADATA_KEY_FILE_NAME));
	        LOG.info("Querying id for product with URI="+fileUri);
	        // must enclose in "..." to execute a perfect string match (in case name contains spaces)
	        String fqval = "CAS.ReferenceDatastore:\""+fileUri+"\"";
	        LOG.info("Querying for fq value="+fqval);
	        request.addFilterQuery(fqval);
	        request.addSortField(Constants.METADATA_KEY_TIMESTAMP, ORDER.desc); // retrieve the last entry with this filepath
	        request.setRows(1); // retrieve only one result
	        LOG.fine("Executing Solr query: "+request.toString());
        
	        // execute Solr query
	        QueryResponse response = solrServers.get(SOLR_CORE_OODT).query( request );
	        SolrDocumentList docs = response.getResults();
	        Iterator<SolrDocument> iter = docs.iterator();
	        while (iter.hasNext()) {
	            SolrDocument doc = iter.next();
	            productId = (String) doc.getFieldValue("id"); 
	            LOG.fine("Retrieved Solr document id="+productId);
	        }
	        
        } catch(Exception e) {
        	e.printStackTrace();
        	LOG.warning(e.getMessage()); // will return empty ids list
        }
		
		return productId;
        
		
	}
	
	/**
	 * Utility method to try to duplicate the specific encoding done by OODT before storing a string
	 * (which is NOT a complete URLEncoder.encode(s,"UTF-8");
	 */
	private static String myencoding(String s) {
	
		return s.replace(" ","%20")
		        .replace("[","%5B")
		        .replace("]","%5D");
		
	}
	
	/**
	 * Utility method to query Solr for a specific product belonging to a dataset and version.
	 * @param datasetId
	 * @param datasetVersion
	 * @param productName
	 * @return
	 */
	public static String queryProduct(String datasetId, String datasetVersion, String productName) {
		
		String id = null;
		
		// build Solr query
        SolrQuery request = new SolrQuery();
        request.setQuery("*:*");
        request.addFilterQuery(Constants.METADATA_KEY_DATASET_ID+":"+datasetId, 
        		               Constants.METADATA_KEY_DATASET_VERSION+":"+datasetVersion, 
        		               Constants.METADATA_KEY_PRODUCT_NAME+":"+productName);
        request.setRows(1);
        
        // execute Solr query
        try {

	        QueryResponse response = solrServers.get(SOLR_CORE_FILES).query( request );
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
	 * Method to publish an OODT ProductType (aka Collection) to Solr.
	 * @param productType
	 * @throws Exception
	 */
	public static void publishCollection(ProductType productType) throws Exception {
		
		FileManagerUtils.printMetadata(productType.getTypeMetadata());
		SolrInputDocument doc = serializeCollection(productType);
		LOG.info("Publishing Solr collection:"+doc.toString());
		solrServers.get(SOLR_CORE_COLLECTIONS).add(doc);
		//solrServers.get(SOLR_CORE_COLLECTIONS).commit(); // use solr.autoSoftCommit.maxTime and solr.autoCommit.maxTime
		
	}
	
	/**
	 * Alternative method to publish a collection into Solr starting from a top-level directory
	 * and looking for "product-types.xml" in all sub-directories.
	 * Note that a single XML file may contain more than one OODT product type.
	 * 
	 * @param productTypeFile
	 * @throws Exception
	 */
	public static void publishCollection(File rootDirectory) {
		
		try {
			if (!rootDirectory.exists() || !rootDirectory.isDirectory()) {
				throw new Exception("Invalid starting directory: "+rootDirectory.getAbsolutePath());
			}
			
			// select policy sub-directories containing file "product-types.xml"
			List<String> policyDirectories = new ArrayList<String>();
			DirectorySelector dirsel = new DirectorySelector(
					Arrays.asList( new String[] {"product-types.xml"} ));
			policyDirectories.addAll( dirsel.traverseDir(new File(rootDirectory.toURI())) );
						
			// parse all XML files using OODT utilities
			XMLRepositoryManager xmlRP = new XMLRepositoryManager(policyDirectories);
			List<ProductType> productTypes = xmlRP.getProductTypes();
			
			// publish all new product types to Solr
			for (ProductType pt : productTypes) {
				SolrUtils.publishCollection(pt);
			}
		} catch(Exception e) {
			LOG.warning(e.getMessage());
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Method to publish dataset-level metadata to Solr.
	 * 
	 * @param productMetadata
	 * @throws Exception
	 */
	public static void publishDataset(Metadata metadata) {
		
		try {
			FileManagerUtils.printMetadata(metadata);
			SolrInputDocument doc = serializeDataset(metadata);
			LOG.info("Publishing Solr dataset:"+doc.toString()+" to Solr core: "+SOLR_URL+"/"+SOLR_CORE_DATASETS);
			solrServers.get(SOLR_CORE_DATASETS).add(doc);
			//solrServers.get(SOLR_CORE_COLLECTIONS).commit(); // use solr.autoSoftCommit.maxTime and solr.autoCommit.maxTime
		} catch(Exception e) {
			LOG.warning(e.getMessage());
			e.printStackTrace();
		}

	}
	
	/**
	 * Method to publish an OODT product (aka file) to Solr.
	 * 
	 * @param metadata
	 * @throws Exception
	 */
	public static void publishFile(Metadata metadata) throws Exception {
		
		// FileManagerUtils.printMetadata(productMetadata);
		SolrInputDocument doc = serializeFile(metadata);
		LOG.info("Publishing product id="+doc.getFieldValue("id")+" to Solr core: "+SOLR_URL+"/"+SOLR_CORE_FILES);
		LOG.info("Document="+doc.toString());
		solrServers.get(SOLR_CORE_FILES).add(doc);
		//solrServers.get(SOLR_CORE_COLLECTIONS).commit(); // use solr.autoSoftCommit.maxTime and solr.autoCommit.maxTime
		
	}
	
	/**
	 * Method that transforms an OODT ProductType into a Solr collection input document.
	 * @param metadata
	 * @return
	 * @throws Exception
	 */
	private static SolrInputDocument serializeCollection(ProductType productType) throws Exception {
		
		SolrInputDocument doc = new SolrInputDocument();
		
		doc.setField("id", productType.getProductTypeId().replaceAll(Constants.EDRN_PREFIX,"")); // note: remove "urn:edrn:"
		
		// serialize all metadata as-is
		// generally multi-valued fields
		Metadata metadata = productType.getTypeMetadata(); 		
		for (String key : metadata.getAllKeys()) {
			
			// ignore OODT book-keeping fields
			if (IGNORED_FIELDS.contains(key))  {
				// do nothing
				
			} else if (key.equals(Constants.METADATA_KEY_PRODUCT_TYPE) || key.equals(Constants.METADATA_KEY_COLLECTION_ID)) {
				// ignore, same as collection "id"
				
			} else {
				// publish all other "|" values into multi-valued field
				for (String value : metadata.getAllMetadata(key)) {
					if (value.indexOf("|")>0) {
						doc.addField(key, Arrays.asList(value.split("\\s*\\|\\s*")));
					} else {
						doc.addField(key, value);
					}
				}
			}
			
		}
		
		// set core fields if not existing
		if (doc.getField(Constants.METADATA_KEY_COLLECTION_NAME)==null)
			doc.addField(Constants.METADATA_KEY_COLLECTION_NAME, productType.getName().replaceAll("_", " "));
		if (doc.getField(Constants.METADATA_KEY_COLLECTION_DESCRIPTION)==null)
			doc.addField(Constants.METADATA_KEY_COLLECTION_DESCRIPTION, productType.getName().replaceAll("_", " "));
		
		return doc;
	}
	
	/**
	 * Method that transforms OODT dataset level metadata into a Solr dataset input document.
	 * @param metadata
	 * @return
	 * @throws Exception
	 */
	private static SolrInputDocument serializeDataset(Metadata metadata) throws Exception {
		
		SolrInputDocument doc = new SolrInputDocument();
		
		// build the composite dataset id
		// FIXME: also use Constants.METADATA_KEY_PARENT_DATASET_ID)
		//String datasetId = metadata.getMetadata(Constants.METADATA_KEY_COLLECTION_ID) 
		//		         + "/" 
		//		         + metadata.getMetadata(Constants.METADATA_KEY_DATASET_ID);
		doc.setField("id", metadata.getMetadata(Constants.METADATA_KEY_DATASET_ID));
		
		// serialize all metadata
		for (String key : metadata.getAllKeys()) {
			
			// ignore OODT book-keeping fields
			if (IGNORED_FIELDS.contains(key))  {
				// do nothing
				
			// harvest Labcas core dataset attributes
			} else if (key.equals(Constants.METADATA_KEY_PRODUCT_TYPE)) {
				// ignore, already have "CollectionName" and "CollectionId"

			} else if (key.equals(Constants.METADATA_KEY_DATASET_ID)) {
				// ignore, id already built
				
			} else if (key.equals(Constants.METADATA_KEY_DATASET_VERSION)) {
				doc.setField(key, metadata.getMetadata(key));
				
			} else {
				// publish all other "|" values into multi-valued field
				// includes "OwnerPrincipal"
				for (String value : metadata.getAllMetadata(key)) {
					
					// must remove HTML tags from value
					value = Jsoup.parse(value).text();

					if (value.indexOf("|")>0) {
						doc.addField(key, Arrays.asList(value.split("\\s*\\|\\s*")));
					} else {
						doc.addField(key, value);
					}
				}
			}

		}
		
		return doc;
		
	}
	
	/**
	 * Method that transforms OODT product level metadata into a Solr file input document.
	 * 
	 * @param metadata
	 * @return
	 * @throws Exception
	 */
	private static SolrInputDocument serializeFile(Metadata metadata) throws Exception {
		
		SolrInputDocument doc = new SolrInputDocument();
		
		// document unique identifier
		String productId = SolrUtils.generateProductId(metadata.getMetadata(Constants.METADATA_KEY_DATASET_ID),
				                                       metadata.getMetadata(Constants.METADATA_KEY_PRODUCT_NAME) );
		doc.setField("id", productId);
		
		// serialize all metadata
		for (String key : metadata.getAllKeys()) {
			
			//LOG.info("Metadata key="+key+" values="+metadata.getMetadata(key));
			
			// ignore OODT book-keeping fields
			if (IGNORED_FIELDS.contains(key))  {
				// do nothing
								
			} else if (key.equals("ProductType")) {
				doc.setField("CollectionId", metadata.getMetadata(key));
												
			//} else if (key.equals("DatasetId")) {
			//	// note: compose the unique dataset identifier
			//	doc.setField("DatasetId", metadata.getMetadata(key));
								
			} else if (key.equals("ProductName")) {
				// ignore, same as Filename
				
			// must switch FileLocation with FilePath
			} else if (key.equals("FileLocation")) {
				// ignore, as it is the original location before archiving
			} else if (key.equals("FilePath")) {
				doc.setField("FileLocation", metadata.getMetadata(key));

			} else if (key.equals("Filename")) {
				// change case
				doc.setField("FileName", metadata.getMetadata(key));
				
		    // transfer specific "pass-through" metadata fields
			// and File* and _File_* fields 
		    // (generally multi-valued)
			} else if (PASS_THROUGH_FIELDS.contains(key)
					   || key.toLowerCase().startsWith("file") 
					   || key.toLowerCase().startsWith("_file_")) {
								
				// publish all other "|" values into multi-valued field
				for (String value : metadata.getAllMetadata(key)) {
					
					// in the metadata values, must transform "&amp;" back to "&"
					// Solr client will take care of properly constructing the XML POST payload
					value = StringEscapeUtils.unescapeXml(value);
					
					// also must remove HTML tags from value
					value = Jsoup.parse(value).text();
					
					if (value.indexOf("|")>0) {
						doc.addField(key.replaceAll("_File_", ""), Arrays.asList(value.split("\\s*\\|\\s*")));
					} else {
						doc.addField(key.replaceAll("_File_", ""), value);
					}
				}
				
			}
		}
		
		// detect file mime type
		String mimeType = MimeTypeUtils.getMimeType( metadata.getMetadata("Filename") );
		if (mimeType != null) {
			doc.setField(Constants.METADATA_KEY_FILE_TYPE, mimeType);
		}
		
		return doc;
		
	}
	
	
	public static String generateProductId(String datasetId, String productName) {
		return datasetId + "/" + productName;
	}
	
	/**
	 * Utility method to build a Solr XML update document 
	 * for all given records and all metadata fields.
	 * @param ids
	 * @param datasetMetadata
	 * @return
	 */
	@Deprecated
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
	@Deprecated
	public static void postSolrXml(String solrXmlDocument) {
		
	    //String strURL = "http://edrn-frontend.jpl.nasa.gov:8080/solr/oodt-fm/update?commit=true";
	    String solrUpdateUrl = SOLR_URL + "/update?commit=true";
	
		HttpClient httpclient = null;
		try {
			httpclient = HttpClients.custom()
				.setSSLContext(new SSLContextBuilder().loadTrustMaterial(null, TrustSelfSignedStrategy.INSTANCE).build())
				.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
				.build();
		} catch (RuntimeException ex) {
			throw ex;
		} catch (Exception ex) {
			System.err.println("I give up in SolrUtils.postSolrXml");
			System.exit(42);
		}


	    HttpPost post = new HttpPost(solrUpdateUrl);
	    
	    try {
		    HttpEntity entity = new ByteArrayEntity(solrXmlDocument.getBytes("UTF-8"));
		    post.setEntity(entity);
		    post.setHeader("Content-Type", "application/xml");
		    HttpResponse response = httpclient.execute(post);
		    String result = EntityUtils.toString(response.getEntity());
		    LOG.info("POST result="+result);
	   
	    } catch(Exception e) {
	    	LOG.warning(e.getMessage());
	    	
	    } finally {   
		    // must release connection
		    post.releaseConnection();
	    }
	    
	}
	
	/**
	 * Command line method to publish all product types (aka collections) from a top-level directory.
	 * Examples (from $LABCAS_HOME/cas-filemgr directory): 
	 * java -Djava.ext.dirs=lib gov.nasa.jpl.edrn.labcas.utils.SolrUtils $LABCAS_HOME/workflows
	 * java -Djava.ext.dirs=lib gov.nasa.jpl.edrn.labcas.utils.SolrUtils $LABCAS_ARCHIVE
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		
		// FIXME: insert help
		String rootDirectory = args[0]; 
		
		// publish all product types in file
		SolrUtils.publishCollection(new File(rootDirectory));
		
	}

}
