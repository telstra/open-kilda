package org.bitbucket.openkilda.pce.provider;

import org.bitbucket.openkilda.messaging.model.Flow;

import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.Set;

/**
 * Storage represents storage interface for FlowManager state.
 */
public interface FlowStorage {
    /**
     * Gets flow.
     *
     * @param flowId flow id
     * @return flow
     */
    ImmutablePair<Flow, Flow> getFlow(String flowId);

    /**
     * Creates flow.
     *
     * @param flow flow
     */
    void createFlow(ImmutablePair<Flow, Flow> flow);

    /**
     * Deletes flow.
     *
     * @param flowId flow id
     */
    void deleteFlow(String flowId);

    /**
     * Updates flow.
     *
     * @param flowId flow id
     * @param flow   flow
     */
    void updateFlow(String flowId, ImmutablePair<Flow, Flow> flow);

    /**
     * Gets all flows.
     *
     * @return all flows
     */
    Set<ImmutablePair<Flow, Flow>> dumpFlows();
}
