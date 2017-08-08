package org.bitbucket.openkilda.pce.path;

import org.bitbucket.openkilda.pce.model.Isl;
import org.bitbucket.openkilda.pce.model.Switch;

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
     * @return {@link Set} of {@link Isl} instances
     */
    LinkedList<Isl> getPath(Switch srcSwitch, Switch dstSwitch, int bandwidth);

    /**
     * Returns intersection between two paths.
     *
     * @param firstPath  first {@link LinkedList} of {@link Isl} instances
     * @param secondPath second {@link LinkedList} of {@link Isl} instances
     * @return intersection {@link Set} of {@link Isl} instances
     */
    Set<Isl> getPathIntersection(LinkedList<Isl> firstPath, LinkedList<Isl> secondPath);

    /**
     * Updates isls available bandwidth.
     *
     * @param path      {@link Set} of {@link Isl} instances
     * @param bandwidth bandwidth
     */
    void updatePathBandwidth(LinkedList<Isl> path, int bandwidth);

    /**
     * Sets network topology.
     *
     * @param network network topology represented by {@link MutableNetwork} instance
     */
    void setNetwork(MutableNetwork<Switch, Isl> network);

    /**
     * Gets isl weight.
     *
     * @param isl isl instance
     * @return isl weight
     */
    Long getWeight(Isl isl);
}
