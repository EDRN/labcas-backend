package gov.nasa.jpl.edrn.labcas.actions;

import java.io.File;

import org.apache.oodt.cas.crawl.action.CrawlerAction;
import org.apache.oodt.cas.crawl.structs.exceptions.CrawlerActionException;
import org.apache.oodt.cas.metadata.Metadata;

import gov.nasa.jpl.edrn.labcas.Constants;
import gov.nasa.jpl.edrn.labcas.utils.SolrUtils;

/**
 * Class that, after a product is successfully ingested into the internal 'oodt-fm' core, 
 * publishes it to the 'files' core for public access.
 * 
 * @author cinquini
 *
 */
public class LabcasPostIngestionAction extends CrawlerAction {
	
    // URL of File Manager instance with default value
    private String fileManagerUrl = "http://localhost:9000/";
	
	@Override
	public boolean performAction(File product, Metadata productMetadata) throws CrawlerActionException {
		
		LOG.info("Performing post-ingest-on-success action for file: "+product.getAbsolutePath());
		LOG.info("OwnerPrincipal: "+productMetadata.getMetadata(Constants.METADATA_KEY_OWNER_PRINCIPAL));
		
		// query OODT Solr catalog to retrieve the product id
		// (which is not made available in the crawler API)
		String productId = SolrUtils.queryProductId(productMetadata);
		LOG.info("Retrieved product id="+productId);
					
		if (productId != null) {
			
			productMetadata.replaceMetadata(Constants.METADATA_KEY_FILE_DOWNLOAD_ID, productId);
			
			// publish product
			try {
				SolrUtils.publishFile(productMetadata);
			} catch (Exception e) {
				throw new CrawlerActionException( e.getMessage() );
			}	
			
			// return success status
			return true;
			
		} else {
			
			// return failure status
			return false;
			
		}
		
		
	}
	
	@Override
	public void validate() throws CrawlerActionException {}
	
    public void setFileManagerUrl(String fileManagerUrl) {
        this.fileManagerUrl = fileManagerUrl;
    }


}
