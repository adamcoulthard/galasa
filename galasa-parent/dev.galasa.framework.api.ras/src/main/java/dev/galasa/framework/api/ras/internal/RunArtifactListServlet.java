/*
 * Copyright contributors to the Galasa project 
 */
package dev.galasa.framework.api.ras.internal;

import org.osgi.service.component.annotations.Component;

import org.osgi.service.component.annotations.ServiceScope;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static dev.galasa.framework.api.ras.internal.ServletErrorMessage.*;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import dev.galasa.framework.spi.IResultArchiveStoreDirectoryService;
import dev.galasa.framework.spi.IRunResult;
import dev.galasa.framework.spi.ResultArchiveStoreException;
import dev.galasa.framework.spi.utils.GalasaGsonBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;



@Component(service = Servlet.class, scope = ServiceScope.PROTOTYPE, property = {
"osgi.http.whiteboard.servlet.pattern=/ras/run/*/artifacts" }, name = "Galasa Runs artifact list microservice")
public class RunArtifactListServlet extends BaseServlet {


	private static final long serialVersionUID = 1L;

	final static Gson gson = GalasaGsonBuilder.build();

	private Log  logger  =  LogFactory.getLog(this.getClass());


	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try{
			Map<String,String[]> paramMap = req.getParameterMap();
			
			String responseBodyJson = retrieveResults(paramMap);

			sendResponse(resp, responseBodyJson, HttpServletResponse.SC_OK);

		} catch (InternalServletException ex ) {
			// the message is a curated servlet message, we intentionally threw up to this level.
			String responseBody = ex.getError().toString();
			int httpFailureCode = ex.getHttpFailureCode();
			sendResponse(resp, responseBody, httpFailureCode);
			logger.error(responseBody,ex);

		} catch (Throwable t){
			// We didn't expect this failure to arrive. So deliver a generic error message.
			String responseBody = new ServletError(GAL5000_GENERIC_API_ERROR).toString();
			int httpFailureCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
			sendResponse(resp, responseBody, httpFailureCode);
			logger.error(responseBody,t);
		}				
	};


	protected String retrieveResults( 
		Map<String,String[]> paramMap
	) throws InternalServletException {

		String runId = extractSingleStringProperty(paramMap, "runId", null );


		IRunResult run = null;
		try {
			run = getRunByRunId(runId);
		} catch (ResultArchiveStoreException e) {
			ServletError error = new ServletError(GAL5002_INVALID_RUN_ID,runId);
			throw new InternalServletException(error, HttpServletResponse.SC_NOT_FOUND);
		}


		JsonArray artifactRecords = new JsonArray();
		
		Path root = null;
		try {
			root = run.getArtifactsRoot();
		} catch( ResultArchiveStoreException ex ) {
		}
		
		for( Path artifactPath : root) {

			JsonObject artifactRecord = new JsonObject();

			artifactRecord.add("runId", new JsonPrimitive(runId));
			artifactRecord.add("path", new JsonPrimitive(artifactPath.toString()));
			artifactRecord.add("url", new JsonPrimitive(artifactPath.toString()));

			artifactRecords.add(artifactRecord);
		}

		String returnedJsonString = gson.toJson(artifactRecords);

		return returnedJsonString;
	}


	private IRunResult getRunByRunId(String id) throws ResultArchiveStoreException {

		IRunResult run = null;

		for (IResultArchiveStoreDirectoryService directoryService : framework.getResultArchiveStore().getDirectoryServices()) {

			run = directoryService.getRunById(id);

			if(run != null) {
				return run;
			}
		}
		return null;
	}
	

}