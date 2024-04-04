/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.ras.couchdb.internal.mocks;

import java.net.URI;

import org.apache.http.impl.client.CloseableHttpClient;

import dev.galasa.ras.couchdb.internal.CouchdbRasException;
import dev.galasa.ras.couchdb.internal.CouchdbValidator;
import dev.galasa.ras.couchdb.internal.dependencies.impl.HttpRequestFactory;

public class MockCouchdbValidator implements CouchdbValidator {

    @Override
    public void checkCouchdbDatabaseIsValid(URI rasUri, CloseableHttpClient httpClient, HttpRequestFactory requestFactory) throws CouchdbRasException {
        // Do nothing.
    }
    
}
