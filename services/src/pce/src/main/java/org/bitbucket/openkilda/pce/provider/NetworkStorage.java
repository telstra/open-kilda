package org.bitbucket.openkilda.pce.provider;

import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;

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
    SwitchInfoData getSwitch(String switchId);

    /**
     * Creates switch.
     *
     * @param newSwitch switch
     */
    void createSwitch(SwitchInfoData newSwitch);

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
    void updateSwitch(String switchId, SwitchInfoData newSwitch);

    /**
     * Gets all switches.
     *
     * @return all switches
     */
    Set<SwitchInfoData> dumpSwitches();

    /**
     * Gets isl.
     *
     * @param islId isl id
     * @return isl
     */
    IslInfoData getIsl(String islId);

    /**
     * Creates isl.
     *
     * @param isl isl
     */
    void createIsl(IslInfoData isl);

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
    void updateIsl(String islId, IslInfoData isl);

    /**
     * Gets all isls.
     *
     * @return all isls
     */
    Set<IslInfoData> dumpIsls();
}
