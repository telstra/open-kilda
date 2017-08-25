package org.bitbucket.openkilda.pce.manager;

import org.bitbucket.openkilda.messaging.model.Isl;
import org.bitbucket.openkilda.messaging.model.Switch;
import org.bitbucket.openkilda.pce.provider.NetworkStorage;
import org.bitbucket.openkilda.pce.provider.PathComputer;

import com.google.common.base.MoreObjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.function.Function;

/**
 * NetworkManager class contains basic operations on network topology.
 */
public class NetworkManager extends NetworkCache {
    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(NetworkManager.class);

    /**
     * {@link NetworkStorage} instance.
     */
    private final NetworkStorage networkStorage;

    /**
     * {@link PathComputer} instance.
     */
    private final PathComputer pathComputer;

    /**
     * Switch change event callback.
     */
    private Function<NetworkManager.SwitchChangeEvent, Void> onSwitchChange;

    /**
     * Isl change event callback.
     */
    private Function<NetworkManager.IslChangeEvent, Void> onIslChange;

    /**
     * Instance constructor.
     *
     * @param networkStorage {@link NetworkStorage} instance
     * @param pathComputer {@link PathComputer} instance
     */
    public NetworkManager(NetworkStorage networkStorage, PathComputer pathComputer) {
        super(networkStorage.dumpSwitches(), networkStorage.dumpIsls());
        this.networkStorage = networkStorage;
        this.pathComputer = pathComputer.withNetwork(getNetwork());
    }

    /**
     * Sets switch change event callback.
     *
     * @param onSwitchChange switch change event callback
     * @return this instance
     */
    public NetworkManager withSwitchChange(Function<SwitchChangeEvent, Void> onSwitchChange) {
        this.onSwitchChange = onSwitchChange;
        return this;
    }

    /**
     * Sets isl change event callback.
     *
     * @param onIslChange isl change event callback
     * @return this instance
     */
    public NetworkManager withIslChange(Function<IslChangeEvent, Void> onIslChange) {
        this.onIslChange = onIslChange;
        return this;
    }

    /**
     * Gets {@link Switch} instance.
     *
     * @param switchId {@link Switch} instance id
     * @return {@link Switch} instance with specified {@link Switch} instance id
     * @throws IllegalArgumentException if {@link Switch} instance with specified id does not exist
     */
    public Switch getSwitch(String switchId) throws IllegalArgumentException {
        return getSwitchCache(switchId);
    }

    /**
     * Creates {@link Switch} instance.
     *
     * @param newSwitch {@link Switch} instance
     * @return created {@link Switch} instance
     * @throws IllegalArgumentException if {@link Switch} instance with specified id already exists
     */
    public Switch createSwitch(Switch newSwitch) throws IllegalArgumentException {
        Switch node = createSwitchCache(newSwitch);
        networkStorage.createSwitch(newSwitch);
        switchChanged(new SwitchChangeEvent(newSwitch, null, null));
        return node;
    }

    /**
     * Updates {@link Switch} instance.
     *
     * @param switchId  {@link Switch} instance id
     * @param newSwitch {@link Switch} instance
     * @return {@link Switch} instance before update
     * @throws IllegalArgumentException if {@link Switch} instance with specified id does not exist
     */
    public Switch updateSwitch(String switchId, Switch newSwitch) throws IllegalArgumentException {
        Switch node = updateSwitchCache(switchId, newSwitch);
        networkStorage.updateSwitch(switchId, newSwitch);
        switchChanged(new SwitchChangeEvent(null, newSwitch, null));
        return node;
    }

    /**
     * Deletes {@link Switch} instance.
     *
     * @param switchId {@link Switch} instance id
     * @return removed {@link Switch} instance
     * @throws IllegalArgumentException if {@link Switch} instance with specified id does not exist
     */
    public Switch deleteSwitch(String switchId) throws IllegalArgumentException {
        Switch node = deleteSwitchCache(switchId);
        networkStorage.deleteSwitch(switchId);
        switchChanged(new SwitchChangeEvent(null, null, node));
        return node;
    }

    /**
     * Gets all {@link Switch} instances.
     *
     * @return {@link Set} of {@link Switch} instances
     */
    public Set<Switch> dumpSwitches() {
        return dumpSwitchesCache();
    }

    /**
     * Get {@link Isl} instance.
     *
     * @param islId {@link Isl} instance id
     * @return {@link Isl} instance with specified {@link Isl} instance id
     * @throws IllegalArgumentException if {@link Isl} instance with specified id does not exist
     */
    public Isl getIsl(String islId) throws IllegalArgumentException {
        return getIslCache(islId);
    }

    /**
     * Creates {@link Isl} instance.
     *
     * @param isl {@link Isl} instance
     * @return {@link Isl} instance previously associated with this {@link Isl} instance id or null otherwise
     * @throws IllegalArgumentException if {@link Switch} instances for {@link Isl} instance do not exist
     */
    public Isl createOrUpdateIsl(Isl isl) throws IllegalArgumentException {
        Isl newIsl;
        String islId = isl.getIslId();
        logger.debug("Create or update {} isl with {} parameters", islId, isl);

        if (cacheContainsIsl(islId)) {
            newIsl = updateIslCache(islId, isl);
            networkStorage.updateIsl(islId, isl);
            islChanged(new IslChangeEvent(null, isl, null));
        } else {
            newIsl = createIslCache(islId, isl);
            networkStorage.createIsl(isl);
            islChanged(new IslChangeEvent(isl, null, null));
        }

        return newIsl;
    }

    /**
     * Deletes {@link Isl} instance.
     *
     * @param islId {@link Isl} instance id
     * @return removed {@link Isl} instance
     * @throws IllegalArgumentException if {@link Isl} instance with specified id does not exist
     */
    public Isl deleteIsl(String islId) throws IllegalArgumentException {
        Isl isl = deleteIslCache(islId);
        networkStorage.deleteIsl(islId);
        islChanged(new IslChangeEvent(null, null, isl));
        return isl;
    }

    /**
     * Gets all {@link Isl} instances.
     *
     * @return {@link Set} of {@link Isl} instances
     */
    public Set<Isl> dumpIsls() {
        return dumpIslsCache();
    }

    /**
     * Handles switch change event.
     *
     * @param event {@link SwitchChangeEvent} instance
     */
    public void handleSwitchChange(SwitchChangeEvent event) {
        if (event.created != null) {
            createSwitchCache(event.created);
        }

        if (event.updated != null) {
            updateSwitchCache(event.updated.getSwitchId(), event.updated);
        }

        if (event.deleted != null) {
            deleteSwitchCache(event.deleted.getSwitchId());
        }
    }

    /**
     * Handles isl change event.
     *
     * @param event {@link IslChangeEvent} instance
     */
    public void handleIslChange(IslChangeEvent event) {
        if (event.created != null) {
            createIslCache(event.created.getIslId(), event.created);
        }

        if (event.updated != null) {
            updateIslCache(event.updated.getIslId(), event.updated);
        }

        if (event.deleted != null) {
            deleteIslCache(event.deleted.getIslId());
        }
    }

    /**
     * Gets path between source and destination switches.
     *
     * @param srcSwitch source {@link Switch} instance
     * @param dstSwitch destination {@link Switch} instance
     * @param bandwidth available bandwidth
     * @return {@link LinkedList} of {@link Isl} instances
     */
    public LinkedList<Isl> getPath(Switch srcSwitch, Switch dstSwitch, int bandwidth) {
        logger.debug("Get single path between source switch {} and destination switch {}", srcSwitch, dstSwitch);
        return pathComputer.getPath(srcSwitch, dstSwitch, bandwidth);
    }

    /**
     * Gets path between source and destination switches.
     *
     * @param srcSwitchId source {@link Switch} id
     * @param dstSwitchId destination {@link Switch} id
     * @param bandwidth   available bandwidth
     * @return {@link LinkedList} of {@link Isl} instances
     */
    public LinkedList<Isl> getPath(String srcSwitchId, String dstSwitchId, int bandwidth) {
        Switch srcSwitch = getSwitchCache(srcSwitchId);
        Switch dstSwitch = getSwitchCache(dstSwitchId);
        return getPath(srcSwitch, dstSwitch, bandwidth);
    }

    /**
     * Returns intersection between two paths.
     *
     * @param firstPath  first {@link LinkedList} of {@link Isl} instances
     * @param secondPath second {@link LinkedList} of {@link Isl} instances
     * @return intersection {@link Set} of {@link Isl} instances
     */
    public Set<Isl> getPathIntersection(LinkedList<Isl> firstPath, LinkedList<Isl> secondPath) {
        logger.debug("Get single path intersection between {} and {}", firstPath, secondPath);
        Set<Isl> intersection = new HashSet<>(firstPath);
        intersection.retainAll(secondPath);
        return intersection;
    }

    /**
     * Updates isls available bandwidth.
     *
     * @param path      {@link Set} of {@link Isl} instances
     * @param bandwidth available bandwidth
     */
    public void updatePathBandwidth(LinkedList<Isl> path, int bandwidth) {
        logger.debug("Update bandwidth {} for path {}", bandwidth, path);
        pathComputer.updatePathBandwidth(path, bandwidth);
    }

    /**
     * Generates event.
     *
     * @param event {@link IslChangeEvent} instance
     */
    private void islChanged(IslChangeEvent event) {
        if (onIslChange != null) {
            onIslChange.apply(event);
        }
    }

    /**
     * Generates event.
     *
     * @param event {@link SwitchChangeEvent} instance
     */
    private void switchChanged(SwitchChangeEvent event) {
        if (onSwitchChange != null) {
            onSwitchChange.apply(event);
        }
    }

    /**
     * Switch changed event representation class.
     */
    class SwitchChangeEvent {
        /** Created switch instance. */
        Switch created;

        /** Updated switch instance. */
        Switch updated;

        /** Deleted switch instance. */
        Switch deleted;

        /**
         * Instance constructor.
         *
         * @param created created switch instance
         * @param updated updated switch instance
         * @param deleted deleted switch instance
         */
        SwitchChangeEvent(Switch created, Switch updated, Switch deleted) {
            this.created = created;
            this.updated = updated;
            this.deleted = deleted;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("created", created)
                    .add("updated", updated)
                    .add("deleted", deleted)
                    .toString();
        }
    }

    /**
     * Isl changed event representation class.
     */
    class IslChangeEvent {
        /** Created isl instance. */
        Isl created;

        /** Updated isl instance. */
        Isl updated;

        /** Deleted isl instance. */
        Isl deleted;

        /**
         * Instance constructor.
         *
         * @param created created isl instance
         * @param updated updated isl instance
         * @param deleted deleted isl instance
         */
        IslChangeEvent(Isl created, Isl updated, Isl deleted) {
            this.created = created;
            this.updated = updated;
            this.deleted = deleted;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("created", created)
                    .add("updated", updated)
                    .add("deleted", deleted)
                    .toString();
        }
    }
}
