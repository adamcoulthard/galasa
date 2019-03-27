package io.ejat.boot.felix;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.felix.bundlerepository.Reason;
import org.apache.felix.bundlerepository.Repository;
import org.apache.felix.bundlerepository.RepositoryAdmin;
import org.apache.felix.bundlerepository.Requirement;
import org.apache.felix.bundlerepository.Resolver;
import org.apache.felix.bundlerepository.Resource;
import org.apache.felix.framework.FrameworkFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;

import io.ejat.boot.LauncherException;
import io.ejat.boot.BootLogger;

/**
 * Felix framework run test class
 */
public class FelixFramework {
	
	private BootLogger logger = new BootLogger();
	
	private boolean loadConsole = Boolean.parseBoolean(System.getProperty("jat.core.load.console", "false")); 

	private Framework framework;

	private Bundle obrBundle;

	private RepositoryAdmin repositoryAdmin;
	
	
	/**
	 * Initialise and start the Felix framework. Install required bundles and the OBRs. Install the eJAT framework bundle
	 * 
	 * @param bundleRepositories the supplied OBRs
	 * @throws LauncherException if there is a problem initialising the framework
	 */
	public void buildFramework(List<String> bundleRepositories, String testBundleName) throws LauncherException {
		logger.debug("Building Felix Framework...");
		
		String felixCacheDirectory = System.getProperty("java.io.tmpdir");
		File felixCache = new File(felixCacheDirectory, "felix-cache");
		try {
			FileUtils.deleteDirectory(felixCache);
			
			FrameworkFactory frameworkFactory = new FrameworkFactory();
			
			HashMap<String, Object> frameworkProperties = new HashMap<>();
			frameworkProperties.put(Constants.FRAMEWORK_STORAGE, felixCache.getAbsolutePath());
			frameworkProperties.put(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.	FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
			frameworkProperties.put(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA, "org.apache.felix.bundlerepository; version=2.1, io.ejat.framework" 
			);
			framework = frameworkFactory.newFramework(frameworkProperties);
			logger.debug("Initializing Felix Framework");
			framework.init();
			logger.debug("Starting Felix Framework");
			framework.start();
			logger.debug("Felix Framework started");
			
			logger.debug("Installing required OSGi bundles");
			
			// Install and start the Felix OBR bundle
			obrBundle = installBundle("org.apache.felix.bundlerepository-2.0.2.jar", true);
			
			// Load the OSGi Bundle Repositories
			loadBundleRepositories(bundleRepositories);

			// Install and start the Felix OSGi console if required
			if (loadConsole) {
				loadBundle("org.apache.felix.gogo.runtime");
				loadBundle("org.apache.felix.gogo.command");
				loadBundle("org.apache.felix.gogo.shell");
			}
			
			loadBundle("log4j");

			// Load the OSGi Service Component Runtime bundle 
			loadBundle("org.apache.felix.scr");

			// Load the ejat-framework bundle
			logger.debug("installing Framework bundle");
			loadBundle("io.ejat.framework");
			
			// Load the test bundle
			logger.debug("Installing Test bundle");
			loadBundle(testBundleName);
			
		} catch(IOException | BundleException e) {
			throw new LauncherException("Unable to initialise the Felix framework", e);
		}
	}

	/**
	 * Run the supplied test
	 * 
	 * @param testBundleName the test bundle name
	 * @param testClassName the test class name
     * @param boostrapProperties the bootstrap properties
     * @param overridesProperties the override properties
	 * @return test passed
	 * @throws LauncherException 
	 * @throws Throwable 
	 */
	public boolean runTest(String testBundleName, String testClassName, Properties boostrapProperties, Properties overridesProperties) throws LauncherException {
		
		boolean testPassed = false;
		
		// Get the framework bundle
		Bundle frameWorkBundle = getBundle("io.ejat.framework");
		
		// Get the io.ejat.framework.TestRunner class service
		String classString = "io.ejat.framework.TestRunner";
		String filterString = "(" + Constants.OBJECTCLASS + "=" + classString + ")";
		ServiceReference<?>[] serviceReferences;
		try {
			serviceReferences = frameWorkBundle.getBundleContext().getServiceReferences(classString, filterString);
		} catch (InvalidSyntaxException e) {
			throw new LauncherException("Unable to get framework service reference", e);
		}
		if (serviceReferences == null || serviceReferences.length != 1) {
			throw new LauncherException("Unable to get single reference to TestRunner service: " + ((serviceReferences == null) ? 0: serviceReferences.length)  + " service(s) returned");
		}
		Object service = frameWorkBundle.getBundleContext().getService(serviceReferences[0]);
		if (service == null) {
			throw new LauncherException("Unable to get TestRunner service");
		}
		
		// Get the  io.ejat.framework.TestRunner#runTest(String testBundleName, String testClassName) method
		Method runTestMethod;
		try {
			runTestMethod = service.getClass().getMethod("runTest", String.class, String.class, Properties.class, Properties.class);
		} catch (NoSuchMethodException | SecurityException e) {
			throw new LauncherException("Unable to get Framework test runner method", e);
		}
		
		// Invoke the runTest method
		logger.debug("Invoking runTest()");
    	try {
			testPassed = (boolean) runTestMethod.invoke(service, testBundleName, testClassName, boostrapProperties, overridesProperties);
		} catch (InvocationTargetException | IllegalAccessException | IllegalArgumentException e) {
			throw new LauncherException(e.getCause());
		}
    	
    	return testPassed;
	}

	/**
	 * Stop the Felix framework
	 * 
	 * @throws LauncherException
	 * @throws InterruptedException 
	 */
	public void stopFramework() throws LauncherException, InterruptedException {
		logger.debug("Stopping Felix framework");
		try {
			framework.stop();
		} catch (BundleException e) {
			throw new LauncherException("Unable to stop the Felix framework", e);
		}

		framework.waitForStop(30000);
		logger.debug("Felix framework stopped");
	}


	/**
	 * Load the supplied OBRs
	 * 
	 * @param bundleRepositories
	 * @throws LauncherException
	 */
	private void loadBundleRepositories(List<String> bundleRepositories) throws LauncherException {
		// Get the OBR RepositoryAdmin service and methods
		ServiceReference<?> serviceReference = obrBundle.getBundleContext().getServiceReference(RepositoryAdmin.class.getName());
		if ((serviceReference != null)) {
			repositoryAdmin = (RepositoryAdmin) obrBundle.getBundleContext().getService(serviceReference);
		} else {
			throw new LauncherException("Unable to get OBR RepositoryAdmin service");
		}
	
		for (String bundleRepository: bundleRepositories) {
			logger.trace("Loading OBR OSGi Bundle Repository " + bundleRepository);
			Repository repository;
			try {
				repository = repositoryAdmin.addRepository(bundleRepository);
			} catch (Exception e) {
				throw new LauncherException("Unable to load repository " + bundleRepository, e);
			}
			
			if (logger.isTraceEnabled()) {
				// Print repository content
				logger.trace("Loaded repository " + repository.getName() + " " + repository.getURI());
				Resource[] resources = repository.getResources();
				String sp3 = "   ";
				for (Resource resource : resources) {
					logger.trace(sp3 + resource.getId());
					logger.trace(sp3 + sp3 + resource.getURI());
					logger.trace(sp3 + sp3 + resource.getSymbolicName());
					logger.trace(sp3 + sp3 + resource.getPresentationName());
					logger.trace(sp3 + sp3 + "requirements:");
					Requirement[] requirements = resource.getRequirements();
					for (Requirement requirement : requirements) {
						logger.trace("        " + requirement.getFilter() + " optional=" + requirement.isOptional());
					}
				}
			}
		}
	}


	/**
	 * Load a bundle from the OSGi Bundle Repository
	 * 
	 * @param bundleSymbolicName
	 * @throws LauncherException 
	 */
	private void loadBundle(String bundleSymbolicName) throws LauncherException {
		
		logger.trace("Installing bundle " + bundleSymbolicName);
		Resolver resolver = repositoryAdmin.resolver();
		String filterString = "(symbolicname=" + bundleSymbolicName + ")";
		Resource[] resources = null;
		try {
			resources = repositoryAdmin.discoverResources(filterString);
		} catch (InvalidSyntaxException e) {
			throw new LauncherException("Unable to discover repoistory resources", e);
		}
		try {
			if (resources.length == 0) {
				throw new LauncherException("Unable to locate bundle \"" + bundleSymbolicName + "\" in OBR repository");
			}
			for (Resource resource : resources) {
				addResource(bundleSymbolicName, resolver, resource);
			}
		} catch (LauncherException e) {
			throw new LauncherException("Unable to install bundle \"" + bundleSymbolicName + "\" from OBR repository", e);
		}
	}


	/**
	 * Add the Resource to the Resolver and resolve
	 * 
	 * @param bundleSymbolicName
	 * @param resolver
	 * @param resource
	 * @throws LauncherException
	 */
	private void addResource(String bundleSymbolicName, Resolver resolver, Resource resource) throws LauncherException {
		logger.trace("Resouce: " + resource);
		resolver.add(resource);
		if (resolver.resolve())
		{
			if (logger.isTraceEnabled()) {
				Resource[] requiredResources = resolver.getRequiredResources();
				for (Resource requiredResource : requiredResources) {
					logger.trace("  RequiredResource: " + requiredResource.getSymbolicName());
				}
				Resource[] optionalResources = resolver.getOptionalResources();
				for (Resource optionalResource : optionalResources) {
					logger.trace("  OptionalResource: " + optionalResource.getSymbolicName());
				}
			}
		    
			resolver.deploy(Resolver.START);
		    
		    if (!isBundleActive(bundleSymbolicName)) {
		    	throw new LauncherException("Bundle failed to install and activate");
		    }
		    
		    printBundles();
		}
		else
		{
		    Reason[] unsatisfiedRequirements = resolver.getUnsatisfiedRequirements();
		    for (Reason reason : unsatisfiedRequirements)
		    {
		    	logger.error(resource.toString() + ": Unable to resolve: " + reason.getRequirement());
		    }
			throw new LauncherException("Unable to resolve bundle " + bundleSymbolicName);
		}
		
	}


	/**
	 * Return the installed Bundle object for the bundle symbolic name
	 * 
	 * @param bundleSymbolicName
	 * @return The bundle object
	 * @throws LauncherException
	 */
	private Bundle getBundle(String bundleSymbolicName) throws LauncherException {
		Bundle[] bundles = framework.getBundleContext().getBundles();
		for (Bundle bundle : bundles) {
			if (bundle.getSymbolicName().contentEquals(bundleSymbolicName)) {
				return bundle;
			}
		}
		
		throw new LauncherException("Unable to find bundle with Bundle-SymbolicName=" + bundleSymbolicName);
	}


	/**
	 * Install a bundle from class path and optionally start it
	 * 
	 * @param bundleJar
	 * @param start
	 * @return The installed bundle
	 * @throws BundleException 
	 * @throws LauncherException 
	 */
	private Bundle installBundle(String bundleJar, boolean start) throws LauncherException, BundleException {
		
		String bundleLocation = null;
		// Bundle location different when running from jar or IDE
		if(isJar()) {
			bundleLocation = this.getClass().getClassLoader().getResource("bundle/" + bundleJar).toExternalForm();
		} else {
			bundleLocation = this.getClass().getClassLoader().getResource("").toString() + "../bundle/" + bundleJar;	
		}
		logger.trace("bundleLocation: " + bundleLocation);
		Bundle bundle = framework.getBundleContext().installBundle(bundleLocation);
		if (start) {
			bundle.start();
		}
		
		printBundles();
		
		return bundle;
	}


	/**
	 * Determine if this class is running from a jar file
	 * 
	 * @return true or false
	 * @throws LauncherException
	 */
	private boolean isJar() throws LauncherException {
		URL resourceURL = this.getClass().getClassLoader().getResource("");
		if (resourceURL == null) {
			resourceURL = this.getClass().getResource("");
		}
		if (resourceURL == null) {
			throw new LauncherException("Unable to determine if running from a jar file");
		}
		logger.trace("isJar resource URL protocol: " + resourceURL.getProtocol());
		return Objects.equals(resourceURL.getProtocol(), "jar");
	}

	/**
	 * Is the supplied active in the OSGi framework
	 * 
	 * @param bundleSymbolicName
	 * @return true or false
	 */
	private boolean isBundleActive(String bundleSymbolicName) {
		Bundle[] bundles = framework.getBundleContext().getBundles();
		for (Bundle bundle : bundles) {
			if (bundle.getSymbolicName().equals(bundleSymbolicName) && bundle.getState() == Bundle.ACTIVE) {
					return true;
			}
		}
		
		return false;
	}


	/** 
	 * Print the currently installed bundles and their state 
	 */
	private void printBundles() {
		if (!logger.isTraceEnabled()) {
			return;
		}
		// Get the bundles
		Bundle[] bundles = framework.getBundleContext().getBundles();
		// Format and print the bundle Id, State, Symbolic name and Version.
		StringBuilder messageBuffer = new StringBuilder(2048);
		messageBuffer.append("Bundle status:");
		
		for (Bundle bundle : bundles) {
			String bundleId = String.valueOf(bundle.getBundleId());
			messageBuffer.append("\n").
					      append(String.format("%5s", bundleId)).
					      append("|").
			  			  append(String.format("%-11s", getBundleStateLabel(bundle))).
					      append("|     |").
			  			  append(bundle.getSymbolicName()).
			  			  append(" (").
			  			  append(bundle.getVersion()).
			  			  append(")");
		}
		
		logger.trace(messageBuffer.toString());
	}
	
	/**
	 * Convert bundle state to string
	 * @param bundle
	 * @return The bundle state
	 */
    private String getBundleStateLabel(Bundle bundle) {
    	switch (bundle.getState()) {
    		case Bundle.UNINSTALLED: return "Uninstalled";
    		case Bundle.INSTALLED: return "Installed";
    		case Bundle.RESOLVED: return "Resolved";
    		case Bundle.STARTING: return "Starting";
    		case Bundle.STOPPING: return "Stopping";
    		case Bundle.ACTIVE: return "Active";
    		default: return "<Unknown (" + bundle.getState() + ")>";
        }
    }
}
