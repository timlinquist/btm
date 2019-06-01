/*
 * Bitronix Transaction Manager
 *
 * Copyright (c) 2010, Bitronix Software.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA 02110-1301 USA
 */
package bitronix.tm.resource;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.transaction.xa.XAResource;

import bitronix.tm.TransactionManagerServices;
import bitronix.tm.recovery.IncrementalRecoverer;
import bitronix.tm.recovery.RecoveryException;
import bitronix.tm.resource.common.XAResourceHolder;
import bitronix.tm.resource.common.XAResourceProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collection of initialized {@link XAResourceProducer}s. All resources must be registered in the {@link ResourceRegistrar}
 * before they can be used by the transaction manager.
 *
 * @author lorban
 */
public class ResourceRegistrar {

    private final static Logger log = LoggerFactory.getLogger(ResourceRegistrar.class);

    private static ReadWriteLock resourcesReadWriteLock = new ReentrantReadWriteLock();
    private static Lock resourcesReadLock = resourcesReadWriteLock.readLock();
    private static Lock resourcesWriteLock = resourcesReadWriteLock.writeLock();
    private static final HashMap<String, XAResourceProducer> resources = new HashMap<>();

    /**
     * Get a registered {@link XAResourceProducer}.
     * @param uniqueName the name of the recoverable resource producer.
     * @return the {@link XAResourceProducer} or null if there was none registered under that name.
     */
    public static XAResourceProducer get(String uniqueName) {
        resourcesReadLock.lock();
        try {
            return resources.get(uniqueName);
        } finally {
            resourcesReadLock.unlock();
        }
    }

    /**
     * Get all {@link XAResourceProducer}s unique names.
     * @return a Set containing all {@link bitronix.tm.resource.common.XAResourceProducer}s unique names.
     */
    public static Set<String> getResourcesUniqueNames() {
        resourcesReadLock.lock();
        try {
            return Collections.unmodifiableSet(new HashSet<>(resources.keySet()));
        } finally {
            resourcesReadLock.unlock();
        }
    }

    /**
     * Register a {@link XAResourceProducer}. If registration happens after the transaction manager started, incremental
     * recovery is run on that resource.
     * @param producer the {@link XAResourceProducer}.
     * @throws bitronix.tm.recovery.RecoveryException when an error happens during recovery.
     */
    public static void register(XAResourceProducer producer) throws RecoveryException {
        String uniqueName = producer.getUniqueName();
        if (uniqueName == null)
            throw new IllegalArgumentException("invalid resource with null uniqueName");

        resourcesWriteLock.lock();
        try {
            if (resources.containsKey(uniqueName))
                throw new IllegalArgumentException("resource with uniqueName '" + producer.getUniqueName() + "' has already been registered");

            if (TransactionManagerServices.isTransactionManagerRunning()) {
                if (log.isDebugEnabled()) log.debug("transaction manager is running, recovering resource " + uniqueName);
                IncrementalRecoverer.recover(producer);
            }
            resources.put(uniqueName, producer);
        } finally {
            resourcesWriteLock.unlock();
        }
    }

    /**
     * Unregister a previously registered {@link XAResourceProducer}.
     * @param producer the {@link XAResourceProducer}.
     */
    public static void unregister(XAResourceProducer producer) {
        String uniqueName = producer.getUniqueName();
        if (uniqueName == null)
            throw new IllegalArgumentException("invalid resource with null uniqueName");

        resourcesWriteLock.lock();
        try {
            if (!resources.containsKey(uniqueName)) {
                if (log.isDebugEnabled()) log.debug("resource with uniqueName '" + producer.getUniqueName() + "' has not been registered");
                return;
            }
            resources.remove(uniqueName);
        } finally {
            resourcesWriteLock.unlock();
        }
    }

    /**
     * Find in the registered {@link XAResourceProducer}s the {@link XAResourceHolder} from which the specified {@link XAResource} comes from.
     * @param xaResource the {@link XAResource} to look for
     * @return the associated {@link XAResourceHolder} or null if it cannot be found.
     */
    public static XAResourceHolder findXAResourceHolder(XAResource xaResource) {
        resourcesReadLock.lock();
        try {
            for (Map.Entry<String, XAResourceProducer> entry : resources.entrySet()) {
                XAResourceProducer producer = entry.getValue();

                XAResourceHolder resourceHolder = producer.findXAResourceHolder(xaResource);
                if (resourceHolder != null) {
                    if (log.isDebugEnabled()) log.debug("XAResource " + xaResource + " belongs to " + resourceHolder + " that itself belongs to " + producer);
                    return resourceHolder;
                }
                if (log.isDebugEnabled()) log.debug("XAResource " + xaResource + " does not belong to any resource of " + producer);
            }
            return null;
        } finally {
            resourcesReadLock.unlock();
        }
    }

    public static void clear() {
        resourcesWriteLock.lock();
        try {
            resources.clear();
        } finally {
            resourcesWriteLock.unlock();
        }
    }
}
