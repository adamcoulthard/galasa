/*
 * Licensed Materials - Property of IBM
 * 
 * (c) Copyright IBM Corp. 2020.
 */
package dev.galasa.kubernetes;

import java.net.InetSocketAddress;

import javax.validation.constraints.NotNull;

/**
 * Represents a Service resource
 * 
 * @author Michael Baylis
 *
 */
public interface IService extends IResource {

    /**
     * Retrieve a Socket Address of a service NodePort which you can use to access the port from outside the cluster.
     * 
     * @param port the nodeport number
     * @return A socket address, never null
     * @throws KubernetesManagerException if there is a comms problem to the cluster or the port cannot be found.
     */
    @NotNull
    InetSocketAddress getSocketAddressForPort(int port) throws KubernetesManagerException;

}
