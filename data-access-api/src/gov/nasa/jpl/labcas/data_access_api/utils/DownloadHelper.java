package gov.nasa.jpl.labcas.data_access_api.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.IOUtils;

/**
 * Class that downloads a file by streaming its contents.
 *
 */
public class DownloadHelper implements StreamingOutput {

	private final static Logger LOG = Logger.getLogger(DownloadHelper.class.getName());
	
	private Path filePath;
	
	public DownloadHelper(Path filePath) {
		this.filePath = filePath;
		// ⚠️ DEBUGGING ONLY for the following three lines (normal operation is line above)
		// String replaced = "/Users/kelly" + filePath.toString().substring("/labcas-data".length());
		// this.filePath = Paths.get(replaced);
		// LOG.info("🪵🪵🪵📄📄📄 filePath is now = " + this.filePath + "‼️");
	}

	public long getFileSize() throws IOException {
		return Files.size(this.filePath);
	}

	@Override
	public void write(OutputStream os) throws IOException, WebApplicationException {

	    LOG.info("🪵🪵🪵📄📄📄 doing copyLarge from " + filePath + " to " + os + "‼️");
		FileInputStream is = new FileInputStream(filePath.toFile());
		IOUtils.copyLarge(is, os);
		os.flush();
	    
	}	
	
}