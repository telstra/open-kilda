package org.bitbucket.openkilda.pce.storage;

import org.bitbucket.openkilda.pce.model.Flow;
import org.bitbucket.openkilda.pce.model.Isl;
import org.bitbucket.openkilda.pce.model.Switch;

import java.util.List;

/**
 * Storage represents storage interface for topology state storage.
 */
public interface Storage {
    /**
     * Returns switch.
     *
     * @param switchId switch id
     * @return switch
     */
    Switch getSwitch(String switchId);

    /**
     * Creates switch.
     *
     * @param newSwitch switch
     */
    void createSwitch(Switch newSwitch);

    /**
     * Deletes switch.
     *
     * @param switchId switch id
     */
    void deleteSwitch(String switchId);

    /**
     * Updates switch.
     *
     * @param switchId switch id
     * @param newSwitch switch
     */
    void updateSwitch(String switchId, Switch newSwitch);

    /**
     * Gets all switches.
     *
     * @return list of switches
     */
    List<Switch> dumpSwitches();

    /**
     * Gets isl.
     *
     * @param islId isl id
     * @return isl
     */
    Isl getIsl(String islId);

    /**
     * Creates isl.
     *
     * @param isl isl
     */
    void createIsl(Isl isl);

    /**
     * Deletes isl.
     *
     * @param islId isl id
     */
    void deleteIsl(String islId);

    /**
     * Updates isl.
     *
     * @param islId isl id
     * @param isl isl
     */
    void updateIsl(String islId, Isl isl);

    /**
     * Gets all isls.
     *
     * @return list of isls
     */
    List<Isl> dumpIsls();

    /**
     * Gets flow.
     *
     * @param flowId flow id
     * @return flow
     */
    Flow getFlow(String flowId);

    /**
     * Creates flow.
     *
     * @param flow flow
     */
    void createFlow(Flow flow);

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
     * @param flow flow
     */
    void updateFlow(String flowId, Flow flow);

    /**
     * Gets all flows.
     *
     * @return list of flows
     */
    List<Flow> dumpFlows();
}
