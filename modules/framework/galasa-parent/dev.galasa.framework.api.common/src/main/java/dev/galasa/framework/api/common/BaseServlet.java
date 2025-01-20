/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.framework.api.common;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import dev.galasa.framework.spi.FrameworkException;
import dev.galasa.framework.spi.utils.GalasaGson;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static dev.galasa.framework.api.common.ServletErrorMessage.*;
import static dev.galasa.framework.spi.rbac.BuiltInAction.*;

public class BaseServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    protected Log logger = LogFactory.getLog(this.getClass());

    static final GalasaGson gson = new GalasaGson();

    private final Map<Pattern, IRoute> routes = new HashMap<>();

    private ResponseBuilder responseBuilder = new ResponseBuilder();

    protected void addRoute(IRoute route) {
        Pattern path = route.getPathRegex();
        logger.info("Base servlet adding route " + path);
        routes.put(path, route);
    }

    protected ResponseBuilder getResponseBuilder() {
        return this.responseBuilder;
    }

    public void setResponseBuilder(ResponseBuilder responseBuilder) {
        this.responseBuilder = responseBuilder;
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        logger.info("BaseServlet: doGet() entered. Url: " + req.getPathInfo());
        processRequest(req, res);
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        logger.info("BaseServlet: doPost() entered");
        processRequest(req, res);
    }

    @Override
    public void doPut(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        logger.info("BaseServlet: doPut() entered");
        processRequest(req, res);
    }

    @Override
    public void doDelete(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        logger.info("BaseServlet: doDelete() entered");
        processRequest(req, res);
    }

    private void processRequest(HttpServletRequest req, HttpServletResponse res) {
        String errorString = "";
        int httpStatusCode = HttpServletResponse.SC_OK;

        try {
            processRoutes(req, res);
        } catch (InternalServletException ex) {
            // the message is a curated servlet message, we intentionally threw up to this level.
            errorString = ex.getMessage();
            httpStatusCode = ex.getHttpFailureCode();
            logger.error(errorString, ex);
        } catch (Throwable t) {
            // We didn't expect this failure to arrive. So deliver a generic error message.
            errorString = new ServletError(GAL5000_GENERIC_API_ERROR).toJsonString();
            httpStatusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            logger.error(errorString, t);
        }

        if (!errorString.isEmpty()) {
            getResponseBuilder().buildResponse(req, res, "application/json", errorString, httpStatusCode);
        }
    }

    private void processRoutes(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException, FrameworkException, InterruptedException {
        String url = req.getPathInfo();
        if (url == null) {
            // There is no path information, so this must be a root path (e.g. /cps)
            url = "";
        }

        QueryParameters queryParameters = new QueryParameters(req.getParameterMap());

        IRoute route = selectRoute(url);

        if (route == null) {
            // No matching route was found, throw a 404 error.
            logger.info("BaseServlet: No matching route found.");
            ServletError error = new ServletError(GAL5404_UNRESOLVED_ENDPOINT_ERROR, url);
            throw new InternalServletException(error, HttpServletResponse.SC_NOT_FOUND);
        } else {
            handleRoute(route, url, queryParameters, req, res);
        }
    }

    private IRoute selectRoute(String url) {
        IRoute routeMatched = null ;

        Iterator<Map.Entry<Pattern, IRoute>> walker = routes.entrySet().iterator();
        while( routeMatched == null && walker.hasNext() ) {
            Map.Entry<Pattern, IRoute> entry = walker.next();

            Pattern routePattern = entry.getKey();
            IRoute possibleMatchedRoute = entry.getValue();

            Matcher matcher = routePattern.matcher(url);

            if (matcher.matches()) {
                routeMatched = possibleMatchedRoute;
            }
        }
        return routeMatched;
    }

    private void handleRoute(
        IRoute route,
        String pathInfo,
        QueryParameters queryParameters,
        HttpServletRequest req,
        HttpServletResponse res
    ) throws ServletException, IOException, FrameworkException {
        String requestMethodStr = req.getMethod();
        HttpMethod requestMethod = HttpMethod.getFromString(requestMethodStr);

        route.validateActionPermitted(GENERAL_API_ACCESS.getAction(), req);

        boolean isBadMethod = false ;
        if (requestMethod == null ) {
            isBadMethod = true ;
        } else {

            switch(requestMethod) {
                case GET: 
                    route.handleGetRequest(pathInfo, queryParameters, req, res);
                    break;
                case POST :
                    route.handlePostRequest(pathInfo, queryParameters, req, res);
                    break;
                case PUT:
                    route.handlePutRequest(pathInfo, queryParameters, req, res);
                    break;
                case DELETE:
                    route.handleDeleteRequest(pathInfo, queryParameters, req, res);
                    break;
                default:
                    isBadMethod = true ;
            }
        }

        if( isBadMethod ) {
            // The request was sent with an unsupported method, so throw an error
            ServletError error = new ServletError(GAL5405_METHOD_NOT_ALLOWED, pathInfo, requestMethodStr);
            throw new InternalServletException(error, HttpServletResponse.SC_METHOD_NOT_ALLOWED);     
        }
    }
}