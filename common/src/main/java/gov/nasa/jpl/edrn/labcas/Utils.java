package gov.nasa.jpl.edrn.labcas;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.logging.Logger;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.oodt.cas.metadata.Metadata;
import org.apache.oodt.cas.metadata.SerializableMetadata;
import org.w3c.dom.Document;

public class Utils {
	
	private static final Logger LOG = Logger.getLogger(Utils.class.getName());
	
	/** 
	 * Utility function to determine the latest version of an archived dataset.
	 * If not found, the latest version is set to 0.
	 */
	public static int findLatestDatasetVersion(final String datasetName) {
		
		String archiveDir = System.getenv(Constants.ENV_LABCAS_ARCHIVE) + "/" + Constants.WORKFLOW_LABCAS_UPOLOAD;
		File datasetDir = new File(archiveDir, datasetName); 
		
        int version = 0;
        if (datasetDir.exists()) {           
	        LOG.fine("Looking for dataset versions in "+datasetDir.getAbsolutePath());
	
	        // list "version" sub-directories
	        String[] directories = datasetDir.list(new FilenameFilter() {
	                  @Override
	                  public boolean accept(File current, String name) {
	                    return new File(current, name).isDirectory();
	                  }
	                });
	        // don't just count the directories, select the highest number
	        for (String dir : directories) {
	        	int v = Integer.parseInt(dir);
	        	if (v > version) version = v;
	        }    
        }
        
        return version;
		
	}
	
	/**
	 * Utility method that reads the additional dataset metadata 
	 * from the file DatasetMetadata.xml located in the dataset staging directory.
	 * 
	 * @param datasetName
	 * @return
	 * @throws IOException
	 */
	public static Metadata readDatasetMetadata(final String datasetName) {
		
        String stagingDir = System.getenv(Constants.ENV_LABCAS_STAGING) + "/" + datasetName;
        File datasetMetadataFile = new File(stagingDir, Constants.METADATA_FILE);
		
		// read input metadata
        Metadata metadata = new Metadata(); // empty metadata container
        if (datasetMetadataFile.exists()) {
        	LOG.info("Updating metadata from file: "+datasetMetadataFile.getAbsolutePath());
        	
        	try {
        		 SerializableMetadata sm = new SerializableMetadata("UTF-8", false);
        		 sm.loadMetadataFromXmlStream(datasetMetadataFile.toURI().toURL().openStream());
        		 metadata = sm.getMetadata();
     			 for (String key : metadata.getAllKeys()) {
    				for (String val : metadata.getAllMetadata(key)) {
    					LOG.fine("\t==> Read dataset metadata key=["+key+"] value=["+val+"]");
    				}
     			 }
        		 
        	} catch (Exception e) {
        		LOG.warning(e.getMessage());
        	}
        	
        } else {
        	LOG.warning("Metadata file: "+datasetMetadataFile.getAbsolutePath()+" not found");
        }
        
        return metadata;
		
	}

	/**
	 * Method to transform an XML document into a pretty-formatted string.
	 * @param xml
	 * @return
	 * @throws Exception
	 */
	public static final String xmlToString(Document xml) throws Exception {
		Transformer tf = TransformerFactory.newInstance().newTransformer();
		tf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		tf.setOutputProperty(OutputKeys.INDENT, "yes");
		Writer out = new StringWriter();
		tf.transform(new DOMSource(xml), new StreamResult(out));
		return out.toString();
	}

}
