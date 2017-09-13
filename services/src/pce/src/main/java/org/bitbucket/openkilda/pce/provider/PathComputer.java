package org.bitbucket.openkilda.pce.provider;

import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathInfoData;
import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;
import org.bitbucket.openkilda.messaging.model.Flow;
import org.bitbucket.openkilda.messaging.model.ImmutablePair;

import com.google.common.graph.MutableNetwork;

import java.io.Serializable;

/**
 * PathComputation interface represent operations on flow path.
 */
public interface PathComputer extends Serializable {
    /**
     * Gets isl weight.
     *
     * @param isl isl instance
     * @return isl weight
     */
    default Long getWeight(IslInfoData isl) {
        return 1L;
    }

    /**
     * Gets path between source and destination switch.
     *
     * @param flow {@link Flow} instances
     * @return {@link PathInfoData} instances
     */
    ImmutablePair<PathInfoData, PathInfoData> getPath(Flow flow);

    /**
     * Gets path between source and destination switch.
     *
     * @param source      source {@link SwitchInfoData} instance
     * @param destination source {@link SwitchInfoData} instance
     * @param bandwidth   available bandwidth
     * @return {@link PathInfoData} instances
     */
    ImmutablePair<PathInfoData, PathInfoData> getPath(SwitchInfoData source, SwitchInfoData destination,
                                                      int bandwidth);

    /**
     * Sets network topology.
     *
     * @param network network topology represented by {@link MutableNetwork} instance
     * @return {@link PathComputer} instance
     */
    PathComputer withNetwork(MutableNetwork<SwitchInfoData, IslInfoData> network);

    /**
     * Initialises path computer.
     */
    void init();
}
