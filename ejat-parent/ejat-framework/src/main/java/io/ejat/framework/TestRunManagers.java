package io.ejat.framework;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.felix.bundlerepository.Reason;
import org.apache.felix.bundlerepository.RepositoryAdmin;
import org.apache.felix.bundlerepository.Resolver;
import org.apache.felix.bundlerepository.Resource;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import io.ejat.framework.spi.FrameworkException;
import io.ejat.framework.spi.IFramework;
import io.ejat.framework.spi.IManager;
import io.ejat.framework.spi.ManagerException;

public class TestRunManagers {

    private final List<IManager> activeManagers = new ArrayList<>();
    private final Log logger = LogFactory.getLog(TestRunManagers.class);
    private final IFramework framework;

    private final BundleContext bundleContext;

    private final RepositoryAdmin repositoryAdmin;


    public TestRunManagers(IFramework framework, Class<?> testClass) throws FrameworkException {
        this.framework = framework;
        this.bundleContext = FrameworkUtil.getBundle(getClass()).getBundleContext();

        ServiceReference<?> serviceReference = bundleContext.getServiceReference(RepositoryAdmin.class.getName());
        repositoryAdmin = (RepositoryAdmin) bundleContext.getService(serviceReference);

        List<IManager> allManagers = locateManagers();
        requestExtraBundlesFromManager(allManagers, allManagers);
        getActiveManagers(allManagers, testClass);

        logger.debug("The following Managers are active:-");
        reportManagers();

        calculateProvisioningDependencies();

        logger.debug("The following Managers are sorted in provisioning order:-");
        reportManagers();

    }

    /**
     * Sort the managers into provisioning order
     * 
     * @throws FrameworkException if there is a circular dependency
     */
    private void calculateProvisioningDependencies() throws FrameworkException {
        ArrayList<IManager> sortedManagers = new ArrayList<>(activeManagers);

        //*** Use a kind of bubble sort to the managers in order
        for(int i = 0; i < sortedManagers.size() - 1; i++) {
            boolean switched = true;
            int limit = sortedManagers.size() - i + 1; //*** Limit the iterations of the switch loop in case there is a direct or indirect dependancy
            while(switched && limit > 0) {
                switched = false;
                limit--;

                IManager testingManager = sortedManagers.get(i);
                for(int j = i + 1; j < sortedManagers.size(); j++) {
                    IManager checkManager = sortedManagers.get(j);
                    if (testingManager.areYouProvisionalDependentOn(checkManager)) {
                        sortedManagers.set(i, checkManager);
                        sortedManagers.set(j, testingManager);
                        switched = true;
                        break;
                    }
                }
            }
        }



        boolean failed = false;
        //*** Final test to ensure all the managers are in the correct order
        for(int i = 0; i < sortedManagers.size(); i++) {
            IManager testingManager = sortedManagers.get(i);
            //*** Make sure that this bean should not be before any of the previous ones
            for(int j = 0; j < i; j++) {
                IManager checkManager = sortedManagers.get(j);
                if (checkManager.areYouProvisionalDependentOn(testingManager)) {
                    failed = true;
                    break;
                }
            }
            if(failed) {
                break;
            }

            //*** Make sure that this bean should not be after any of the following ones
            for(int j = sortedManagers.size() - 1; i < j; j--) {
                IManager checkManager = sortedManagers.get(j);
                if (testingManager.areYouProvisionalDependentOn(checkManager)) {
                    failed = true;
                    break;
                }
            }
            if(failed) {
                break;
            }
        }

        if (failed) {
            logger.fatal("The Managers were unable to be sorted into provisioning order");
            logger.fatal("The resulting order was:-");

            for(IManager manager : sortedManagers) {
                logger.fatal("   " + manager.getClass().getName());
            }

            throw new FrameworkException("Unable to sort managers into order for provisioning");
        }

        activeManagers.clear();
        activeManagers.addAll(sortedManagers);
    }

    private void reportManagers() {
        for(IManager manager : activeManagers) {
            logger.debug("   " + manager.getClass().getName());
        }

    }

    private void getActiveManagers(List<IManager> allManagers, Class<?> testClass) throws FrameworkException {
        //*** Ask each one to initialise itself if required and chain request other managers
        for(IManager manager : allManagers) {
            try {
                manager.initialise(framework, allManagers, activeManagers, testClass);
            } catch (ManagerException e) {
                throw new FrameworkException("Unable to initialise Manager " + manager.getClass().getName(), e);
            }
        }

    }

    private void requestExtraBundlesFromManager(List<IManager> managersToCheck, List<IManager> allManagers) throws FrameworkException {
        ArrayList<String> extraBundles = new ArrayList<>();

        //*** First ask all these managers if there are any extra OSGi Bundles to load
        for(IManager manager : managersToCheck) {
            List<String> newExtraBundles;
			try {
				newExtraBundles = manager.extraBundles(framework);
			} catch (ManagerException e) {
				throw new FrameworkException("Problem requesting extra bundles from managers",e);
			}

            if (newExtraBundles == null) {
                continue;
            }

            for(String extraBundle : newExtraBundles) {
                if (!extraBundles.contains(extraBundle) 
                        && !isBundleActive(extraBundle)) {
                    extraBundles.add(extraBundle);
                }
            }
        }

        //*** If none of this batch of managers requested extra bundles, then we are done
        if (extraBundles.isEmpty()) {
            return;
        }

        //*** Attempt to load all the extra bundles
        for(String extraBundle : extraBundles) {
            loadBundle(extraBundle);
        }

        //*** Just incase,  relocate all the managers again to find the new ones
        List<IManager> newManagers = locateManagers();
        Iterator<IManager> it = newManagers.iterator();
        while(it.hasNext()) {
            IManager nextManager = it.next();
            if (allManagers.contains(nextManager)) {
                it.remove();
            }
        }

        //*** No new managers
        if (newManagers.isEmpty()) {
            return;
        }

        //*** Add all our new managers to the established list
        allManagers.addAll(newManagers);

        //*** Process the new managers incase they have extra bundles
        requestExtraBundlesFromManager(newManagers, allManagers);
    }

    /**
     * Locate all the Managers that are in OSGi
     * 
     * @return All the managers available and loaded
     * @throws FrameworkException if a problem with the OSGi Service Reference component
     */
    private List<IManager> locateManagers() throws FrameworkException {
        ArrayList<IManager> managers = new ArrayList<>();

        try {
            final ServiceReference<?>[] managerServiceReference = bundleContext.getAllServiceReferences(IManager.class.getName(), null);
            if ((managerServiceReference == null) || (managerServiceReference.length == 0)) {
                return managers;
            }
            for (final ServiceReference<?> managerReference : managerServiceReference) {
                final IManager managerService = (IManager) bundleContext.getService(managerReference);
                managers.add(managerService);
            }
        } catch(InvalidSyntaxException e) {
            throw new FrameworkException("Unable to locate Managers", e);
        }

        return managers;
    }


    /**
     * Load a bundle from the OSGi Bundle Repository
     * 
     * @param bundleSymbolicName
     * @throws LauncherException 
     */
    private void loadBundle(String bundleSymbolicName) throws FrameworkException {

        logger.trace("Installing bundle " + bundleSymbolicName);
        Resolver resolver = repositoryAdmin.resolver();
        String filterString = "(symbolicname=" + bundleSymbolicName + ")";
        Resource[] resources = null;
        try {
            resources = repositoryAdmin.discoverResources(filterString);
        } catch (InvalidSyntaxException e) {
            throw new FrameworkException("Unable to discover repoistory resources", e);
        }
        try {
            if (resources.length == 0) {
                throw new FrameworkException("Unable to locate bundle \"" + bundleSymbolicName + "\" in OBR repository");
            }
            for (Resource resource : resources) {
                addResource(bundleSymbolicName, resolver, resource);
            }
        } catch (FrameworkException e) {
            throw new FrameworkException("Unable to install bundle \"" + bundleSymbolicName + "\" from OBR repository", e);
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
    private void addResource(String bundleSymbolicName, Resolver resolver, Resource resource) throws FrameworkException {
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
                throw new FrameworkException("Bundle failed to install and activate");
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
            throw new FrameworkException("Unable to resolve bundle " + bundleSymbolicName);
        }

    }


    /**
     * Is the supplied active in the OSGi framework
     * 
     * @param bundleSymbolicName
     * @return true or false
     */
    private boolean isBundleActive(String bundleSymbolicName) {
        Bundle[] bundles = bundleContext.getBundles();
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
        Bundle[] bundles = bundleContext.getBundles();
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

    public boolean anyReasonTestClassShouldBeIgnored() throws FrameworkException {
        for(IManager manager : activeManagers) {
            try {
                String reason = manager.anyReasonTestClassShouldBeIgnored();
                if (reason != null) {
                    logger.info("Ignoring class due to " + reason);
                    return true;
                }
            } catch (ManagerException e) {
                throw new FrameworkException("Unable to calculate Test Class ignore status", e);
            }
        }
        return false;
    }

    public void provisionGenerate() throws FrameworkException {
        for(IManager manager : activeManagers) {
            try {
                manager.provisionGenerate();
            } catch (ManagerException e) {
                throw new FrameworkException("Problem in provision generate for manager " + manager.getClass().getName(), e);
            }
        }
    }

    public void provisionBuild() throws FrameworkException {
        for(IManager manager : activeManagers) {
            try {
                manager.provisionBuild();
            } catch (ManagerException e) {
                throw new FrameworkException("Problem in provision build for manager " + manager.getClass().getName(), e);
            }
        }
    }

    public void provisionStart() throws FrameworkException {
        for(IManager manager : activeManagers) {
            try {
                manager.provisionStart();
            } catch (ManagerException e) {
                throw new FrameworkException("Problem in provision start for manager " + manager.getClass().getName(), e);
            }

        }
    }

    public void provisionStop() {
        for(IManager manager : activeManagers) {
            manager.provisionStop();
        }
    }

    public void provisionDiscard() {
        for(IManager manager : activeManagers) {
            manager.provisionDiscard();
        }
    }

    public void startOfTestClass() throws FrameworkException {
        for(IManager manager : activeManagers) {
            try {
                manager.startOfTestClass();
            } catch (ManagerException e) {
                throw new FrameworkException("Problem in start of test class for manager " + manager.getClass().getName(), e);
            }
        }
    }

    public boolean anyReasonTestMethodShouldBeIgnored(@NotNull Method method) throws FrameworkException {
        for(IManager manager : activeManagers) {
            try {
                String reason = manager.anyReasonTestMethodShouldBeIgnored(method);
                if (reason != null) {
                    logger.info("Ignoring method due to " + reason);
                    return true;
                }
            } catch (ManagerException e) {
                throw new FrameworkException("Unable to calculate Test Method ignore status", e);
            }
        }
        return false;
    }

    public void fillAnnotatedFields(Object testClassObject) throws FrameworkException {
        for(IManager manager : activeManagers) {
            try {
                manager.fillAnnotatedFields(testClassObject);
            } catch (ManagerException e) {
                throw new FrameworkException("Problem in fill annotated fields for manager " + manager.getClass().getName(), e);
            }
        }
    }

    public void startOfTestMethod(@NotNull Method testMethod) throws FrameworkException {
        for(IManager manager : activeManagers) {
            try {
                manager.startOfTestMethod();
            } catch (ManagerException e) {
                throw new FrameworkException("Problem in start of test test method for manager " + manager.getClass().getName(), e);
            }
        }
    }

    public String endOfTestMethod(@NotNull String currentResult, Throwable currentException) throws FrameworkException {
        String newResult = null;

        for(IManager manager : activeManagers) {
            try {
                String managerResult = manager.endOfTestMethod(currentResult, currentException);
                if (managerResult != null && newResult == null) {
                    newResult = managerResult;
                }
            } catch (ManagerException e) {
                throw new FrameworkException("Problem in end of test method for manager " + manager.getClass().getName(), e);
            }
        }

        return newResult;
    }

    public String endOfTestClass(@NotNull String currentResult, Throwable currentException) throws FrameworkException {
        String newResult = null;

        for(IManager manager : activeManagers) {
            try {
                String managerResult = manager.endOfTestClass(currentResult, currentException);
                if (managerResult != null && newResult == null) {
                    newResult = managerResult;
                }
            } catch (ManagerException e) {
                throw new FrameworkException("Problem in end of test class for manager " + manager.getClass().getName(), e);
            }

        }

        return newResult;
    }

    public void endOfTestRun() {
        for(IManager manager : activeManagers) {
            manager.endOfTestRun();
        }
    }

}
