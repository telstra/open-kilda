package org.bitbucket.openkilda.pce.provider;

import org.bitbucket.openkilda.messaging.model.Isl;
import org.bitbucket.openkilda.messaging.model.Switch;

import com.google.common.graph.MutableNetwork;

import java.util.LinkedList;
import java.util.Set;

/**
 * PathComputation interface represent operations on flow path.
 */
public interface PathComputer {
    /**
     * Gets path between source and destination switch.
     *
     * @param srcSwitch source {@link Switch} instance
     * @param dstSwitch destination {@link Switch} instance
     * @param bandwidth available bandwidth
     * @return {@link Set} of {@link Isl} instances
     */
    LinkedList<Isl> getPath(Switch srcSwitch, Switch dstSwitch, int bandwidth);

    /**
     * Updates isls available bandwidth.
     *
     * @param path      {@link Set} of {@link Isl} instances
     * @param bandwidth available bandwidth
     */
    void updatePathBandwidth(LinkedList<Isl> path, int bandwidth);

    /**
     * Sets network topology.
     *
     * @param network network topology represented by {@link MutableNetwork} instance
     * @return {@link PathComputer} instance
     */
    PathComputer withNetwork(MutableNetwork<Switch, Isl> network);

    /**
     * Gets isl weight.
     *
     * @param isl isl instance
     * @return isl weight
     */
    Long getWeight(Isl isl);
}
