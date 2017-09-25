package gov.nasa.jpl.labcas.data_access_api.service;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;

/**
 * Service used to create list of files to be downloaded.
 * 
 * @author Luca Cinquini
 *
 */
public interface DownloadService {
	
	/**
	 * Method to download all files belonging to collections that match some query criteria.
	 * 
	 * @param httpRequest
	 * @param q
	 * @param fq
	 * @param start
	 * @param rows
	 * @param sort
	 * @return
	 */
	public Response downloadCollections(HttpServletRequest httpRequest, ContainerRequestContext requestContext, String q, List<String> fq, int start, int rows);
	
	/**
	 * Method to download all files belonging to datasets that match some query criteria.
	 * 
	 * @param httpRequest
	 * @param q
	 * @param fq
	 * @param start
	 * @param rows
	 * @param sort
	 * @return
	 */
	public Response downloadDatasets(HttpServletRequest httpRequest, ContainerRequestContext requestContext, String q, List<String> fq, int start, int rows);
	
	/**
	 * Method to download all files matching some query criteria.
	 * 
	 * @param httpRequest
	 * @param q
	 * @param fq
	 * @param start
	 * @param rows
	 * @param sort
	 * @return
	 */
	public Response downloadFiles(HttpServletRequest httpRequest, ContainerRequestContext requestContext, String q, List<String> fq, int start, int rows);

}