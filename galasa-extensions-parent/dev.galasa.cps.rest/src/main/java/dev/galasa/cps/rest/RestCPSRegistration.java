/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.cps.rest;

import java.net.*;
import java.text.MessageFormat;

import javax.validation.constraints.NotNull;

import org.osgi.service.component.annotations.Component;

import dev.galasa.extensions.common.api.HttpClientFactory;
import dev.galasa.extensions.common.api.LogFactory;
import dev.galasa.extensions.common.impl.HttpClientFactoryImpl;
import dev.galasa.extensions.common.impl.LogFactoryImpl;
import dev.galasa.framework.spi.ConfigurationPropertyStoreException;
import dev.galasa.framework.spi.IConfigurationPropertyStoreRegistration;
import dev.galasa.framework.spi.IFrameworkInitialisation;

@Component(service = { IConfigurationPropertyStoreRegistration.class })
public class RestCPSRegistration implements IConfigurationPropertyStoreRegistration {

    private HttpClientFactory httpClientFacotory; 
    private JwtProvider jwtProvider;
    private LogFactory logFactory;

    public RestCPSRegistration() {
        this( 
            new HttpClientFactoryImpl() , 
            new JwtProviderEnvironment(),
            new LogFactoryImpl() 
        );
    }

    public RestCPSRegistration( 
        HttpClientFactory httpClientFacotory,
        JwtProvider jwtProvider,
        LogFactory logFactory
    ) {
        this.httpClientFacotory = httpClientFacotory;
        this.jwtProvider = jwtProvider;
        this.logFactory = logFactory ;
    }

    /**
     * <p>
     * This method checks that the CPS is a remote URL reference, and if true registers this
     * file as the ONLY CPS.
     * </p>
     * 
     * @param frameworkInitialisation Parameters this extension can use to to initialise itself.
     * @throws ConfigurationPropertyStoreException Something went wrong.
     */
    @Override
    public void initialise(@NotNull IFrameworkInitialisation frameworkInitialisation)
            throws ConfigurationPropertyStoreException {

        URI cpsEndpointUri = frameworkInitialisation.getBootstrapConfigurationPropertyStore();

        if (isUriRefferringToThisExtension(cpsEndpointUri)) {

            // strip off the schema part of the URL, to give a real URL of the API server on the ecosystem.
            // The URI starts with "galasacps:" so we have to replace that with 'https'.
            // We eventually turn that into something like https://my.ecosystem.server/api on which the REST API should
            // be reachable.
            String ecosystemRestApiStr=cpsEndpointUri.toString();

            URI ecosystemRestApi = null ;
            try {
                ecosystemRestApi = new URI(ecosystemRestApiStr);
            } catch(URISyntaxException ex) {
                String msg = MessageFormat.format("Failed to parse the CPS rest URL '{0}'. It should be of the form '{1}://my.server/api' or similar.",ecosystemRestApiStr,RestCPS.URL_SCHEMA_REST);
                throw new ConfigurationPropertyStoreException(msg,ex);
            }

            frameworkInitialisation.registerConfigurationPropertyStore(
                new RestCPS(
                    ecosystemRestApi, 
                    httpClientFacotory,
                    jwtProvider,
                    logFactory
                )
            );
        }
    }

    /**
     * <p>
     * A simple method thta checks the provided URI to the CPS is a local file or
     * not.
     * </p>
     * 
     * @param uri - URI to the CPS . of the form "rest:http://my.server/api"
     * @return - boolean if File or not.
     */
    public boolean isUriRefferringToThisExtension(@NotNull URI uri) {
        return RestCPS.URL_SCHEMA_REST.equals(uri.getScheme());
    }
}
