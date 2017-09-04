package org.bitbucket.openkilda.pce.manager;

import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathNode;
import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;
import org.bitbucket.openkilda.pce.provider.NetworkStorage;
import org.bitbucket.openkilda.pce.provider.PathComputer;

import com.google.common.base.MoreObjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
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
     * @param pathComputer   {@link PathComputer} instance
     */
    public NetworkManager(NetworkStorage networkStorage, PathComputer pathComputer) {
        load(networkStorage.dumpSwitches(), networkStorage.dumpIsls());
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
     * Gets {@link SwitchInfoData} instance.
     *
     * @param switchId {@link SwitchInfoData} instance id
     * @return {@link SwitchInfoData} instance with specified {@link SwitchInfoData} instance id
     * @throws IllegalArgumentException if {@link SwitchInfoData} instance with specified id does not exist
     */
    public SwitchInfoData getSwitch(String switchId) throws IllegalArgumentException {
        return getSwitchCache(switchId);
    }

    /**
     * Creates {@link SwitchInfoData} instance.
     *
     * @param newSwitch {@link SwitchInfoData} instance
     * @return created {@link SwitchInfoData} instance
     * @throws IllegalArgumentException if {@link SwitchInfoData} instance with specified id already exists
     */
    public SwitchInfoData createSwitch(SwitchInfoData newSwitch) throws IllegalArgumentException {
        SwitchInfoData node = createSwitchCache(newSwitch);
        networkStorage.createSwitch(newSwitch);
        switchChanged(new SwitchChangeEvent(newSwitch, null, null));
        return node;
    }

    /**
     * Updates {@link SwitchInfoData} instance.
     *
     * @param switchId  {@link SwitchInfoData} instance id
     * @param newSwitch {@link SwitchInfoData} instance
     * @return {@link SwitchInfoData} instance before update
     * @throws IllegalArgumentException if {@link SwitchInfoData} instance with specified id does not exist
     */
    public SwitchInfoData updateSwitch(String switchId, SwitchInfoData newSwitch) throws IllegalArgumentException {
        SwitchInfoData node = updateSwitchCache(switchId, newSwitch);
        networkStorage.updateSwitch(switchId, newSwitch);
        switchChanged(new SwitchChangeEvent(null, newSwitch, null));
        return node;
    }

    /**
     * Deletes {@link SwitchInfoData} instance.
     *
     * @param switchId {@link SwitchInfoData} instance id
     * @return removed {@link SwitchInfoData} instance
     * @throws IllegalArgumentException if {@link SwitchInfoData} instance with specified id does not exist
     */
    public SwitchInfoData deleteSwitch(String switchId) throws IllegalArgumentException {
        SwitchInfoData node = deleteSwitchCache(switchId);
        networkStorage.deleteSwitch(switchId);
        switchChanged(new SwitchChangeEvent(null, null, node));
        return node;
    }

    /**
     * Gets all {@link SwitchInfoData} instances.
     *
     * @return {@link Set} of {@link SwitchInfoData} instances
     */
    public Set<SwitchInfoData> dumpSwitches() {
        return dumpSwitchesCache();
    }

    /**
     * Get {@link IslInfoData} instance.
     *
     * @param islId {@link IslInfoData} instance id
     * @return {@link IslInfoData} instance with specified {@link IslInfoData} instance id
     * @throws IllegalArgumentException if {@link IslInfoData} instance with specified id does not exist
     */
    public IslInfoData getIsl(String islId) throws IllegalArgumentException {
        return getIslCache(islId);
    }

    /**
     * Creates {@link IslInfoData} instance.
     *
     * @param isl {@link IslInfoData} instance
     * @return {@link IslInfoData} instance previously associated with {@link IslInfoData} instance id or null otherwise
     * @throws IllegalArgumentException if {@link SwitchInfoData} instances for {@link IslInfoData} do not exist
     */
    public IslInfoData createOrUpdateIsl(IslInfoData isl) throws IllegalArgumentException {
        IslInfoData newIsl;
        String islId = isl.getId();
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
     * Deletes {@link IslInfoData} instance.
     *
     * @param islId {@link IslInfoData} instance id
     * @return removed {@link IslInfoData} instance
     * @throws IllegalArgumentException if {@link IslInfoData} instance with specified id does not exist
     */
    public IslInfoData deleteIsl(String islId) throws IllegalArgumentException {
        IslInfoData isl = deleteIslCache(islId);
        networkStorage.deleteIsl(islId);
        islChanged(new IslChangeEvent(null, null, isl));
        return isl;
    }

    /**
     * Gets all {@link IslInfoData} instances.
     *
     * @return {@link Set} of {@link IslInfoData} instances
     */
    public Set<IslInfoData> dumpIsls() {
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
            createIslCache(event.created.getId(), event.created);
        }

        if (event.updated != null) {
            updateIslCache(event.updated.getId(), event.updated);
        }

        if (event.deleted != null) {
            deleteIslCache(event.deleted.getId());
        }
    }

    /**
     * Gets path between source and destination switches.
     *
     * @param srcSwitch source {@link SwitchInfoData} instance
     * @param dstSwitch destination {@link SwitchInfoData} instance
     * @param bandwidth available bandwidth
     * @return {@link PathInfoData} instance
     */
    public PathInfoData getPath(SwitchInfoData srcSwitch, SwitchInfoData dstSwitch, int bandwidth) {
        logger.debug("Get single path between source switch {} and destination switch {}", srcSwitch, dstSwitch);
        return pathComputer.getPath(srcSwitch, dstSwitch, bandwidth);
    }

    /**
     * Gets path between source and destination switches.
     *
     * @param srcSwitchId source {@link SwitchInfoData} id
     * @param dstSwitchId destination {@link SwitchInfoData} id
     * @param bandwidth   available bandwidth
     * @return {@link PathInfoData} instances
     */
    public PathInfoData getPath(String srcSwitchId, String dstSwitchId, int bandwidth) {
        SwitchInfoData srcSwitch = getSwitchCache(srcSwitchId);
        SwitchInfoData dstSwitch = getSwitchCache(dstSwitchId);
        return getPath(srcSwitch, dstSwitch, bandwidth);
    }

    /**
     * Returns intersection between two paths.
     *
     * @param firstPath  first {@link PathInfoData} instances
     * @param secondPath second {@link PathInfoData} instances
     * @return intersection {@link Set} of {@link IslInfoData} instances
     */
    public Set<PathNode> getPathIntersection(PathInfoData firstPath, PathInfoData secondPath) {
        logger.debug("Get single path intersection between {} and {}", firstPath, secondPath);
        Set<PathNode> intersection = new HashSet<>(firstPath.getPath());
        intersection.retainAll(secondPath.getPath());
        return intersection;
    }

    /**
     * Updates isls available bandwidth.
     *
     * @param path      {@link Set} of {@link IslInfoData} instances
     * @param bandwidth available bandwidth
     */
    public void updatePathBandwidth(PathInfoData path, int bandwidth) {
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
        SwitchInfoData created;

        /** Updated switch instance. */
        SwitchInfoData updated;

        /** Deleted switch instance. */
        SwitchInfoData deleted;

        /**
         * Instance constructor.
         *
         * @param created created switch instance
         * @param updated updated switch instance
         * @param deleted deleted switch instance
         */
        SwitchChangeEvent(SwitchInfoData created, SwitchInfoData updated, SwitchInfoData deleted) {
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
        IslInfoData created;

        /** Updated isl instance. */
        IslInfoData updated;

        /** Deleted isl instance. */
        IslInfoData deleted;

        /**
         * Instance constructor.
         *
         * @param created created isl instance
         * @param updated updated isl instance
         * @param deleted deleted isl instance
         */
        IslChangeEvent(IslInfoData created, IslInfoData updated, IslInfoData deleted) {
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
