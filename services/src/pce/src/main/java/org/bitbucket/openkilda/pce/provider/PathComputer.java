package org.bitbucket.openkilda.pce.provider;

import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathInfoData;
import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;

import com.google.common.graph.MutableNetwork;

/**
 * PathComputation interface represent operations on flow path.
 */
public interface PathComputer {
    /**
     * Gets path between source and destination switch.
     *
     * @param srcSwitch source {@link SwitchInfoData} instance
     * @param dstSwitch destination {@link SwitchInfoData} instance
     * @param bandwidth available bandwidth
     * @return {@link PathInfoData} instances
     */
    PathInfoData getPath(SwitchInfoData srcSwitch, SwitchInfoData dstSwitch, int bandwidth);

    /**
     * Updates isls available bandwidth.
     *
     * @param path      {@link PathInfoData} instances
     * @param bandwidth available bandwidth
     */
    void updatePathBandwidth(PathInfoData path, int bandwidth);

    /**
     * Sets network topology.
     *
     * @param network network topology represented by {@link MutableNetwork} instance
     * @return {@link PathComputer} instance
     */
    PathComputer withNetwork(MutableNetwork<SwitchInfoData, IslInfoData> network);

    /**
     * Gets isl weight.
     *
     * @param isl isl instance
     * @return isl weight
     */
    Long getWeight(IslInfoData isl);
}
