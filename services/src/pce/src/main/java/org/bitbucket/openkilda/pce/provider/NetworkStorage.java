package org.bitbucket.openkilda.pce.provider;

import org.bitbucket.openkilda.messaging.model.Isl;
import org.bitbucket.openkilda.messaging.model.Switch;

import java.util.Set;

/**
 * Storage represents storage interface for NetworkManager state.
 */
public interface NetworkStorage {
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
     * @param switchId  switch id
     * @param newSwitch switch
     */
    void updateSwitch(String switchId, Switch newSwitch);

    /**
     * Gets all switches.
     *
     * @return all switches
     */
    Set<Switch> dumpSwitches();

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
     * @param isl   isl
     */
    void updateIsl(String islId, Isl isl);

    /**
     * Gets all isls.
     *
     * @return all isls
     */
    Set<Isl> dumpIsls();
}
