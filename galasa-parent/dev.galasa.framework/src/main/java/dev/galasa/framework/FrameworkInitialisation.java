/*
 * Copyright contributors to the Galasa project 
 */
package dev.galasa.framework;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.Map.Entry;
import java.nio.file.*;
import javax.validation.constraints.NotNull;
import org.apache.commons.logging.*;
import org.osgi.framework.*;
import dev.galasa.framework.spi.*;
import dev.galasa.framework.spi.creds.*;

public class FrameworkInitialisation implements IFrameworkInitialisation {

    private static final String                      USER_HOME = "user.home";
    private static final String                      GALASA_HOME = "GALASA_HOME";

    private Framework                                framework;

    private final Properties                         bootstrapProperties;

    private final URI                                uriConfigurationPropertyStore;
    private final URI                                uriDynamicStatusStore;
    private final URI                                uriCredentialsStore;
    private final List<URI>                          uriResultArchiveStores;

    private final IConfigurationPropertyStoreService cpsFramework;
    private final IDynamicStatusStoreService         dssFramework;
    // private final ICredentialsStoreService credsFramework;

    private Log logger;
    private IFileSystem fileSystem ;

    private String galasaHome;

    
    public FrameworkInitialisation(
        Properties bootstrapProperties, 
        Properties overrideProperties
    ) throws URISyntaxException, InvalidSyntaxException, FrameworkException {
        this(bootstrapProperties, overrideProperties, false, null, 
        getBundleContext() , new FileSystem() );
    }


    public FrameworkInitialisation(
        Properties bootstrapProperties, 
        Properties overrideProperties, 
        boolean testrun
    ) throws URISyntaxException, InvalidSyntaxException, FrameworkException {
        this(bootstrapProperties, overrideProperties, testrun, null, 
        getBundleContext() , new FileSystem());
    }


    public FrameworkInitialisation(
        Properties bootstrapProperties, 
        Properties overrideProperties,
        boolean testrun,
        Log initLogger
    ) throws URISyntaxException, InvalidSyntaxException, FrameworkException {
        this(bootstrapProperties, overrideProperties, testrun, initLogger, 
        getBundleContext(), new FileSystem());
    }


    private static BundleContext getBundleContext() {
        return FrameworkUtil.getBundle(FrameworkInitialisation.class).getBundleContext();
    }


    public FrameworkInitialisation(
        Properties bootstrapProperties, 
        Properties overrideProperties, 
        boolean testrun,
        Log initLogger,
        BundleContext bundleContext , 
        IFileSystem fileSystem
    ) throws URISyntaxException, InvalidSyntaxException, FrameworkException {

        this.bootstrapProperties = bootstrapProperties;
        this.fileSystem = fileSystem;

        //load galasa home from the bootstrap
        this.galasaHome = bootstrapProperties.getProperty("framework.galasa.home");

        // *** Copy the the bootstrap properties to the override properties so that they
        // are available to the managers
        overrideProperties.putAll(bootstrapProperties);

        if (initLogger == null) {
            logger = LogFactory.getLog(this.getClass());
        } else {
            logger = initLogger;
        }

        logger.info("Initialising the Galasa Framework");

        this.framework = locateFramework(bundleContext);
        assertFrameworkNotAlreadyInitialised(this.framework);

        this.framework.setFrameworkProperties(overrideProperties);

        // *** If this is a test run, then we need to install the log4j capture routine
        if (testrun) {
            this.framework.installLogCapture();
        }

        this.uriConfigurationPropertyStore = locateConfigurationPropertyStore(
            this.logger, overrideProperties, this.fileSystem);
        this.cpsFramework = initialiseConfigurationPropertyStore(logger,bundleContext);

        this.uriDynamicStatusStore = locateDynamicStatusStore(
            this.logger, overrideProperties, this.cpsFramework, this.fileSystem);
        this.dssFramework = initialiseDynamicStatusStore(logger,bundleContext);

        if (testrun) {
            // *** Is this a test run,
            // *** Then we need to make sure we have a runname for the RAS. If there isnt
            // one, we need to allocate one
            // *** Need the DSS for this as the latest run number number is stored in there
            String runName = locateRunName(this.cpsFramework);
            this.framework.setTestRunName(runName);
        }

        this.uriResultArchiveStores = createUriResultArchiveStores(overrideProperties, this.cpsFramework);
        logger.debug("Result Archive Stores are " + this.uriResultArchiveStores.toString());
        initialiseResultsArchiveStore(logger,bundleContext);

        this.uriCredentialsStore = locateCredentialsStore(
            this.logger,overrideProperties,this.cpsFramework,this.fileSystem);
        initialiseCredentialsStore(logger,bundleContext);

        initialiseConfidentialTextService(logger,bundleContext);
                
        if (framework.isInitialised()) {
            logger.info("Framework initialised");
        } else {
            logger.info("The Framework does not think it is initialised, but we didn't get any errors");
        }

        // *** If this is a test run, add the overrides from the run dss properties to
        // these overrides
        if (testrun) {
            String prefix = "run." + framework.getTestRunName() + ".override.";
            int len = prefix.length();

            Map<String, String> runOverrides = this.dssFramework.getPrefix(prefix);
            for (Entry<String, String> override : runOverrides.entrySet()) {
                String key = override.getKey().substring(len);
                String value = override.getValue();

                if (logger.isTraceEnabled()) {
                    logger.trace("Setting run override " + key + "=" + value);
                }
                overrideProperties.put(override.getKey(), override.getValue());
            }
        }
    }

    /**
     * Submit the run and return the run name.
     * 
     * @param runBundleClass
     * @param language
     * @return The name of the run created.
     * @throws FrameworkException
     */
    protected String submitRun(String runBundleClass, String language) throws FrameworkException {
        IRun run = null;
        IFrameworkRuns frameworkRuns = this.framework.getFrameworkRuns();

        switch(language) {
            case "java": 
                String split[] = runBundleClass.split("/");
                String bundle = split[0];
                String test = split[1];
                run = frameworkRuns.submitRun("local", null, bundle, test, null, null, null, null, true, false, null, null, null, language);
                break;
            case "gherkin":
                run = frameworkRuns.submitRun("local", null, null, runBundleClass, null, null, null, null, true, false, null, null, null, language);
                break;
            default:
                throw new FrameworkException("Unknown language to create run");
        }

        logger.info("Allocated Run Name " + run.getName() + " to this run");

        return run.getName();
    }

    /**
     * Create an empty default property file if it doesn't already exist
     * 
     * @param propertyFile
     * @throws IOException
     */
    private void createIfMissing(URI propertyFile, IFileSystem fileSystem) {

        Path path = Paths.get(propertyFile);
        try {
            if (!fileSystem.exists(path)) {
                // Create the parent folders if they don't exist.
                if (! fileSystem.exists(path.getParent()) ) {
                    fileSystem.createDirectories(path.getParent());
                }
                // Create an empty file.
                logger.info("File "+path.toString()+" does not exist, so creating it.");
                fileSystem.createFile(path);
            }
        } catch (IOException e) {
            logger.error("Unable to create empty default property file " + path.toUri().toString(), e);
        }
    }

    private Framework locateFramework(BundleContext bundleContext) throws FrameworkException {
        ServiceReference<IFramework> frameworkService = bundleContext.getServiceReference(IFramework.class);
        if (frameworkService == null) {
            throw new FrameworkException("The framework service is missing");
        }
        return (Framework) bundleContext.getService(frameworkService);
    }

    /*
     * (non-Javadoc)
     *
     * @see dev.galasa.framework.spi.IFrameworkInitialisation#
     * getBootstrapConfigurationPropertyStore()
     */
    @Override
    public @NotNull URI getBootstrapConfigurationPropertyStore() {
        return this.uriConfigurationPropertyStore;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * dev.galasa.framework.spi.IFrameworkInitialisation#getDynamicStatusStoreUri()
     */
    @Override
    public URI getDynamicStatusStoreUri() {
        return this.uriDynamicStatusStore;
    }

    @Override
    public URI getCredentialsStoreUri() {
        return this.uriCredentialsStore;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * dev.galasa.framework.spi.IFrameworkInitialisation#getResultArchiveStoreUris()
     */
    @Override
    public List<URI> getResultArchiveStoreUris() {
        return this.uriResultArchiveStores;
    }

    /*
     * (non-Javadoc)
     *
     * @see dev.galasa.framework.spi.IFrameworkInitialisation#
     * registerConfigurationPropertyStoreService(dev.galasa.framework.spi.
     * IConfigurationPropertyStoreService)
     */
    @Override
    public void registerConfigurationPropertyStore(@NotNull IConfigurationPropertyStore configurationPropertyStore)
            throws ConfigurationPropertyStoreException {
        this.framework.setConfigurationPropertyStore(configurationPropertyStore);
    }

    /*
     * (non-Javadoc)
     *
     * @see dev.galasa.framework.spi.IFrameworkInitialisation#
     * registerDynamicStatusStoreService(dev.galasa.framework.spi.
     * IDynamicStatusStoreService)
     */
    @Override
    public void registerDynamicStatusStore(@NotNull IDynamicStatusStore dynamicStatusStore)
            throws DynamicStatusStoreException {
        this.framework.setDynamicStatusStore(dynamicStatusStore);
    }

    /*
     * (non-Javadoc)
     *
     * @see dev.galasa.framework.spi.IFrameworkInitialisation#
     * registerResultArchiveStoreService(dev.galasa.framework.spi.
     * IResultArchiveStoreService)
     */
    @Override
    public void registerResultArchiveStoreService(@NotNull IResultArchiveStoreService resultArchiveStoreService)
            throws ResultArchiveStoreException {
        this.framework.addResultArchiveStoreService(resultArchiveStoreService);

    }

    @Override
    public void registerConfidentialTextService(@NotNull IConfidentialTextService confidentialTextService)
            throws ConfidentialTextException {
        this.framework.setConfidentialTextService(confidentialTextService);
    }

    @Override
    public void registerCredentialsStore(@NotNull ICredentialsStore credentialsStore) throws CredentialsException {
        this.framework.setCredentialsStore(credentialsStore);
    }

    /*
     * (non-Javadoc)
     *
     * @see dev.galasa.framework.spi.IFrameworkInitialisation#getFramework()
     */
    @Override
    public @NotNull IFramework getFramework() {
        return this.framework;
    }

    public void shutdownFramework() {
        try {
            this.framework.shutdown(null);
        } catch(Exception e) {
            logger.fatal("Problem shutting down the Galasa framework",e);
        }
    }

	@Override
	public void registerCertificateStoreService(@NotNull ICertificateStoreService certificateStoreService)
			throws CertificateStoreException {
		
	}

    private void assertFrameworkNotAlreadyInitialised(Framework framework) throws FrameworkException {
        if (this.framework.isInitialised()) {
            throw new FrameworkException("The framework has already been initialised");
        }
    }

    /**
     * Find where the property store is located, or create a new one if it's not
     * 
     * Note: package level scope so we can unit test it.
     * @param logger
     * @param bootstrapProperties
     * @return
     * @throws URISyntaxException
     */
    URI locateConfigurationPropertyStore(
        Log logger ,
        Properties overrideProperties,
        IFileSystem fileSystem
    ) throws URISyntaxException {

        URI storeUri ;
        String propUri = overrideProperties.getProperty("framework.config.store");
        if ((propUri != null) && (!propUri.isEmpty())) {
            logger.debug("bootstrap property framework.config.store used to determine CPS location.");
        }

        if ((propUri == null) || propUri.isEmpty()) {
            Path path = Paths.get(this.galasaHome , "cps.properties");
            storeUri = path.toUri();
            logger.debug("galasa home used to determine CPS location.");
            createIfMissing(storeUri,fileSystem);
        } else {
            storeUri = new URI(propUri);
        }
        logger.debug("Configuration Property Store is " + storeUri.toString());
        return storeUri ;
    }


    // Find where the DSS should be, creating a new blank one if it's not already there.
    // Note: Not private so we can easily unit test it.
    URI locateDynamicStatusStore(
        Log logger,
        Properties overrideProperties,
        IConfigurationPropertyStoreService cpsFramework,
        IFileSystem fileSystem
    ) throws FrameworkException {

        URI uriDynamicStatusStore = null;
        try {
            String dssProperty = overrideProperties.getProperty("framework.dynamicstatus.store");
            if((dssProperty != null) && !dssProperty.isEmpty()){
                uriDynamicStatusStore = new URI(dssProperty);
                logger.debug("Dynamic Status Store is " + uriDynamicStatusStore.toString());
                createIfMissing(uriDynamicStatusStore, fileSystem);
                return uriDynamicStatusStore;
            }

            dssProperty = cpsFramework.getProperty("dynamicstatus", "store");
            if((dssProperty != null) && !dssProperty.isEmpty()){
                uriDynamicStatusStore = new URI(dssProperty);
                logger.debug("Dynamic Status Store is " + uriDynamicStatusStore.toString());
                createIfMissing(uriDynamicStatusStore, fileSystem);
                return uriDynamicStatusStore;
            }

            uriDynamicStatusStore = Paths.get(this.galasaHome, "dss.properties").toUri();
            logger.debug("Dynamic Status Store is " + uriDynamicStatusStore.toString());
            createIfMissing(uriDynamicStatusStore,fileSystem);
            return uriDynamicStatusStore;
        } catch (final Exception e) {
            throw new FrameworkException("Unable to resolve the Dynamic Status Store URI", e);
        }
    }

    // Find the run name of the test run, if it's not a set property 
    // ("framework.run.name")
    // then create a run name by submitting the run, based on language, properties.
    private String locateRunName(IConfigurationPropertyStoreService cpsFramework) throws FrameworkException {
        //*** Ensure the shared environment = true is set for Shenv runs
        String runName = AbstractManager.nulled(cpsFramework.getProperty("run", "name"));
        if (runName == null) {
            String testName = AbstractManager.nulled(cpsFramework.getProperty("run", "testbundleclass"));
            String testLanguage  = "java";
            if (testName == null) {
                testName = AbstractManager.nulled(cpsFramework.getProperty("run", "gherkintest"));
                testLanguage = "gherkin";
            }
            logger.info("Submitting test "+testName);
            runName = submitRun(testName, testLanguage);
        }
        logger.info("Run name is "+runName);
        return runName;
    }


    /**
     * Creates a list of URIs which refer to Result Archive Stores.
     *
     * @param cpsFramework
     * @return
     * @throws FrameworkException
     */
    List<URI> createUriResultArchiveStores(
        Properties overrideProperties,
        IConfigurationPropertyStoreService cpsFramework
    ) throws FrameworkException {

        ArrayList<URI> uriResultArchiveStores = new ArrayList<>(1);

        Path localRasPath = Paths.get(this.galasaHome, "ras");
        URI localRasUri = localRasPath.toUri();
        try {

<<<<<<< HEAD
            String rasProperty = overrideProperties.getProperty("framework.resultarchive.store");
=======
            String rasProperty = bootstrapProperties.getProperty("framework.resultarchive.store");
>>>>>>> 9b352623 (updates to initialisation)
            if((rasProperty != null) && !rasProperty.isEmpty()){
                final String[] rasPaths = rasProperty.split(",");
                for (final String rasPath : rasPaths) {
                    if (!rasPath.trim().isEmpty()) {
                        logger.debug("Adding Result Archive Store location " + uriDynamicStatusStore.toString());
                        uriResultArchiveStores.add(new URI(rasPath));
                    }
                }
            }

            rasProperty = cpsFramework.getProperty("resultarchive", "store");
            if((rasProperty != null) && !rasProperty.isEmpty()){
                final String[] rasPaths = rasProperty.split(",");
                for (final String rasPath : rasPaths) {
                    if (!rasPath.trim().isEmpty()) {
                        logger.debug("Adding Result Archive Store location " + uriDynamicStatusStore.toString());
                        uriResultArchiveStores.add(new URI(rasPath));
                    }
                }
            }

            if(uriResultArchiveStores.size() == 0) {
                // Neither environment nor cps have set the RAS location.
                // Default to a local RAS within galasaHome
                uriResultArchiveStores.add(localRasUri);
            }

            if(uriResultArchiveStores.isEmpty()){
                throw new FrameworkException("No Result Archive Store URIs were provided");
            }
        } catch (final FrameworkException e) {
            throw e;
        } catch (final Exception e) {
            throw new FrameworkException("Unable to resolve the Result Archive Store URIs", e);
        }


        // If resultarchive.store.include.default.local is set to TRUE (case insensitive)
        // then make sure the local RAS store is included in the list of RAS stores.
        boolean includeLocal = Boolean.parseBoolean(
            cpsFramework.getProperty("resultarchive.store", "include.default.local"));
        if (includeLocal) {
            if (! uriResultArchiveStores.contains(localRasUri)) {
                uriResultArchiveStores.add(localRasUri);
            }
        }

        return uriResultArchiveStores;
    }


    /**
     * Find the credentials store, creating it if it doesn't already exist.
     * @param logger
     * @param cpsFramework
     * @param fileSystem
     * @return
     * @throws FrameworkException
     */
    URI locateCredentialsStore(
        Log logger,
        Properties overrideProperties,
        IConfigurationPropertyStoreService cpsFramework,
        IFileSystem fileSystem
    ) throws FrameworkException {
        
        URI uriCredentialsStore ;
        // *** Work out the creds uri
        try {
<<<<<<< HEAD
            String credsProperty = overrideProperties.getProperty("framework.credentials.store");
=======
            String credsProperty = bootstrapProperties.getProperty("framework.credentials.store");
>>>>>>> 9b352623 (updates to initialisation)
            if((credsProperty != null) && !credsProperty.isEmpty()){
                uriCredentialsStore = new URI(credsProperty);
                logger.debug("Credentials Store is " + uriCredentialsStore.toString());
                createIfMissing(uriCredentialsStore, fileSystem);
                return uriCredentialsStore;
            }

            credsProperty = cpsFramework.getProperty("credentials", "store");
            if((credsProperty != null) && !credsProperty.isEmpty()){
                uriCredentialsStore = new URI(credsProperty);
                logger.debug("Credentials Store is " + uriCredentialsStore.toString());
                createIfMissing(uriCredentialsStore, fileSystem);
                return uriCredentialsStore;
            }
            uriCredentialsStore = Paths.get(this.galasaHome, "credentials.properties").toUri();
            createIfMissing(uriCredentialsStore,fileSystem);
            
        } catch (final Exception e) {
            throw new FrameworkException("Unable to resolve the Credentials Store URI", e);
        }
        logger.debug("Credentials Store is " + uriCredentialsStore.toString());
        return uriCredentialsStore;
    }


    IConfigurationPropertyStoreService initialiseConfigurationPropertyStore(
        Log logger, BundleContext bundleContext ) throws FrameworkException,InvalidSyntaxException {

        logger.trace("Searching for CPS providers");
        final ServiceReference<?>[] cpsServiceReference = bundleContext
                .getAllServiceReferences(IConfigurationPropertyStoreRegistration.class.getName(), null);
        if ((cpsServiceReference == null) || (cpsServiceReference.length == 0)) {
            throw new FrameworkException("No Configuration Property Store Services have been found");
        }
        for (final ServiceReference<?> cpsReference : cpsServiceReference) {
            final IConfigurationPropertyStoreRegistration cpsStoreRegistration = (IConfigurationPropertyStoreRegistration) bundleContext
                    .getService(cpsReference);
            logger.trace("Found CPS Provider " + cpsStoreRegistration.getClass().getName());
            // Call out to the cpsStoreRegistration. 
            // We expect it to call back on the IFrameworkInitialisation.registerConfigurationPropertyStore call
            // to set the CPS object into the this.framework, so it can be retrieved by the 
            // this.framework.getConfigurationPropertyStore() call in a bit...
            cpsStoreRegistration.initialise(this);
        }
        if (this.framework.getConfigurationPropertyStore() == null) {
            throw new FrameworkException("Failed to initialise a Configuration Property Store, unable to continue");
        }
        logger.debug(
                "Selected CPS Service is " + this.framework.getConfigurationPropertyStore().getClass().getName());

        return this.framework.getConfigurationPropertyService("framework");
    }


    IDynamicStatusStoreService initialiseDynamicStatusStore(Log logger, BundleContext bundleContext ) throws InvalidSyntaxException, FrameworkException {

        logger.trace("Searching for DSS providers");
        final ServiceReference<?>[] dssServiceReference = bundleContext
                .getAllServiceReferences(IDynamicStatusStoreRegistration.class.getName(), null);
        if ((dssServiceReference == null) || (dssServiceReference.length == 0)) {
            throw new FrameworkException("No Dynamic Status Store Services have been found");
        }
        for (final ServiceReference<?> dssReference : dssServiceReference) {
            final IDynamicStatusStoreRegistration dssStoreRegistration = (IDynamicStatusStoreRegistration) bundleContext
                    .getService(dssReference);
            logger.trace("Found DSS Provider " + dssStoreRegistration.getClass().getName());
            // Some magic here: The dssStoreRegistration calls us back into the register the dss store,
            // which gets put into the Framework object, so when we call framework.getDynamicStatusStoreService()
            // it isn't null !
            dssStoreRegistration.initialise(this);
        }
        if (this.framework.getDynamicStatusStore() == null) {
            throw new FrameworkException("Failed to initialise a Dynamic Status Store, unable to continue");
        }
        logger.trace("Selected DSS Service is " + this.framework.getDynamicStatusStore().getClass().getName());
        
        return this.framework.getDynamicStatusStoreService("framework");
    }  


    void initialiseResultsArchiveStore(Log logger, BundleContext bundleContext) throws FrameworkException, InvalidSyntaxException {
        this.logger.trace("Searching for RAS providers");
        final ServiceReference<?>[] rasServiceReference = bundleContext
                .getAllServiceReferences(IResultArchiveStoreRegistration.class.getName(), null);
        if ((rasServiceReference == null) || (rasServiceReference.length == 0)) {
            throw new FrameworkException("No Result Archive Store Services have been found");
        }
        for (final ServiceReference<?> rasReference : rasServiceReference) {
            final IResultArchiveStoreRegistration rasRegistration = (IResultArchiveStoreRegistration) bundleContext
                    .getService(rasReference);
            logger.trace("Found RAS Provider " + rasRegistration.getClass().getName());
            // Magic here: The ras Registration calls back to this.registerResultArchiveStoreService()
            // which in turn sets the value into the framework, so that 
            // ramework.getResultArchiveStoreService() returns non-null.
            rasRegistration.initialise(this);
        }
        if (this.framework.getResultArchiveStoreService() == null) {
            throw new FrameworkException("Failed to initialise a Result Archive Store, unable to continue");
        }
        logger.trace("Selected RAS Service is " + this.framework.getResultArchiveStoreService().getClass().getName());
    }


    void initialiseCredentialsStore(
        Log logger, 
        BundleContext bundleContext 
    ) throws FrameworkException, InvalidSyntaxException {

        // *** Initialise the Credentials Store
        logger.trace("Searching for Creds providers");
        final ServiceReference<?>[] credsServiceReference = bundleContext
                .getAllServiceReferences(ICredentialsStoreRegistration.class.getName(), null);
        if ((credsServiceReference == null) || (credsServiceReference.length == 0)) {
            throw new FrameworkException("No Credentials Services have been found");
        }
        for (final ServiceReference<?> credsReference : credsServiceReference) {
            final ICredentialsStoreRegistration credsRegistration = (ICredentialsStoreRegistration) bundleContext
                    .getService(credsReference);
            logger.trace("Found Creds Provider " + credsRegistration.getClass().getName());
            // Magic happens here; The registration code calls back here to registerCredentialsStore()
            // which in turn pushes that to the framework, so later on, 
            // framework.getCredentialsStore() returns non-null.
            credsRegistration.initialise(this);
        }
        if (this.framework.getCredentialsStore() == null) {
            throw new FrameworkException("Failed to initialise a Credentials Store, unable to continue");
        }
        logger.trace("Selected Credentials Service is " + this.framework.getCredentialsStore().getClass().getName());
    }


    void initialiseConfidentialTextService(
        Log logger, 
        BundleContext bundleContext 
    ) throws FrameworkException, InvalidSyntaxException {

        // *** Initialise the Confidential Text Service
        logger.trace("Searching for Confidential Text Service providers");
        final ServiceReference<?>[] confidentialServiceReference = bundleContext
                .getAllServiceReferences(IConfidentialTextServiceRegistration.class.getName(), null);
        if ((confidentialServiceReference == null) || (confidentialServiceReference.length == 0)) {
            throw new FrameworkException("No Confidential Text Services have been found");
        }
        for (final ServiceReference<?> confidentialReference : confidentialServiceReference) {
            final IConfidentialTextServiceRegistration credsRegistration = (IConfidentialTextServiceRegistration) bundleContext
                    .getService(confidentialReference);
            logger.trace("Found Confidential Text Services Provider " + credsRegistration.getClass().getName());
            // Magic happens here; The registration code calls back here to registerConfidentialTextstore()
            // which in turn pushes that to the framework, so later on, 
            // framework.getConfidentialTextService returns non-null.
            credsRegistration.initialise(this);
        }
        if (this.framework.getConfidentialTextService() == null) {
            throw new FrameworkException("Failed to initialise a Confidential Text Services, unable to continue");
        }
        logger.trace("Selected Confidential Text Service is "
                + this.framework.getConfidentialTextService().getClass().getName());

    }
}   