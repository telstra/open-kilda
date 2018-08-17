/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.floodlight.switchmanager;

import org.openkilda.messaging.command.switches.ConnectModeRequest;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.payload.flow.OutputVlanType;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsReply;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.List;
import java.util.Map;


public interface ISwitchManager extends IFloodlightService {
    /** OVS software switch manufacturer constant value. */
    String OVS_MANUFACTURER = "Nicira, Inc.";
    long DROP_RULE_COOKIE = 0x8000000000000001L;
    long VERIFICATION_BROADCAST_RULE_COOKIE = 0x8000000000000002L;
    long VERIFICATION_UNICAST_RULE_COOKIE = 0x8000000000000003L;

    /**
     * Set connection mode.
     *
     * @param mode the mode to use, if not null
     * @return the connection mode after the set operation (if not null)
     */
    ConnectModeRequest.Mode connectMode(final ConnectModeRequest.Mode mode);


    /**
     * Adds default rules to install verification rules and final drop rule.
     * Essentially, it calls installDropFlow and installVerificationRule twice (ie isBroadcast)
     *
     * @param dpid datapathId of switch
     * @throws SwitchOperationException in case of errors
     */
    void installDefaultRules(final DatapathId dpid) throws SwitchOperationException;

    /**
     * Installs the default verification rule, if it is allowed. One case where it isn't -
     * if the switch is an OpenFlow 1.2 switch and isBroadcast = false. In that scenario, nothing
     * happens.
     */
    void installVerificationRule(final DatapathId dpid, final boolean isBroadcast)
            throws SwitchOperationException;

    /**
     * Installs the default drop rule.
     *
     * @param dpid datapathId of switch
     * @throws SwitchOperationException in case of errors
     */
    void installDropFlow(final DatapathId dpid) throws SwitchOperationException;

    /**
     * Installs custom drop rule .. ie cookie, priority, match
     *
     * @param dpid datapathId of switch
     * @param dstMac Destination Mac address to match on
     * @param dstMask Destination Mask to match on
     * @param cookie Cookie to use for this rule
     * @param priority Priority of the rule
     */
    void installDropFlowCustom(final DatapathId dpid, String dstMac, String dstMask,
                               final long cookie, final int priority) throws SwitchOperationException;


    /**
     * Installs an flow on ingress switch.
     *
     * @param dpid          datapathId of the switch
     * @param flowId        flow id
     * @param inputPort     port to expect the packet on
     * @param outputPort    port to forward the packet out
     * @param inputVlanId   input vlan to match on, 0 means not to match on vlan
     * @param transitVlanId vlan to add before outputing on outputPort
     * @return transaction id
     * @throws SwitchOperationException Switch not found
     */
    long installIngressFlow(final DatapathId dpid, final String flowId, final Long cookie,
                                                    final int inputPort, final int outputPort, final int inputVlanId,
                                                    final int transitVlanId, final OutputVlanType outputVlanType,
                                                    final long meterId) throws SwitchOperationException;

    /**
     * Installs flow on egress swtich.
     *
     * @param dpid           datapathId of the switch
     * @param flowId         flow id
     * @param inputPort      port to expect the packet on
     * @param outputPort     port to forward the packet out
     * @param transitVlanId  vlan to match on the ingressPort
     * @param outputVlanId   set vlan on packet before forwarding via outputPort; 0 means not to set
     * @param outputVlanType type of action to apply to the outputVlanId if greater than 0
     * @return transaction id
     * @throws SwitchOperationException Switch not found
     */
    long installEgressFlow(final DatapathId dpid, final String flowId, final Long cookie,
                                                   final int inputPort, final int outputPort, final int transitVlanId,
                                                   final int outputVlanId, final OutputVlanType outputVlanType)
            throws SwitchOperationException;

    /**
     * Installs flow on a transit switch.
     *
     * @param dpid          datapathId of the switch
     * @param flowId        flow id
     * @param inputPort     port to expect packet on
     * @param outputPort    port to forward packet out
     * @param transitVlanId vlan to match on inputPort
     * @return transaction id
     * @throws SwitchOperationException Switch not found
     */
    long installTransitFlow(final DatapathId dpid, final String flowId, final Long cookie,
                                                    final int inputPort, final int outputPort, final int transitVlanId)
            throws SwitchOperationException;

    /**
     * Installs flow through one switch.
     *
     * @param dpid           datapathId of the switch
     * @param flowId         flow id
     * @param inputPort      port to expect packet on
     * @param outputPort     port to forward packet out
     * @param inputVlanId    vlan to match on inputPort
     * @param outputVlanId   set vlan on packet before forwarding via outputPort; 0 means not to set
     * @param outputVlanType type of action to apply to the outputVlanId if greater than 0
     * @return transaction id
     * @throws SwitchOperationException Switch not found
     */
    long installOneSwitchFlow(final DatapathId dpid, final String flowId, final Long cookie,
                                                      final int inputPort, final int outputPort, int inputVlanId,
                                                      int outputVlanId, final OutputVlanType outputVlanType,
                                                      final long meterId) throws SwitchOperationException;

    /**
     * Returns list of installed flows.
     *
     * @param dpid switch id
     * @return OF flow stats entries
     */
    List<OFFlowStatsEntry> dumpFlowTable(final DatapathId dpid);

    /**
     * Returns list of installed meters.
     *
     * @param dpid switch id
     * @return OF meter config stats entries
     * @throws SwitchOperationException Switch not found
     */
    OFMeterConfigStatsReply dumpMeters(final DatapathId dpid) throws SwitchOperationException;

    /**
     * Installs a meter on ingress switch OF_13.
     * TODO: describe params meaning in accordance with OF
     *
     * @param dpid      datapath ID of the switch
     * @param bandwidth the bandwidth limit value
     * @param burstSize the size of the burst
     * @param meterId   the meter ID
     * @return transaction id
     * @throws SwitchOperationException Switch not found
     */
    long installMeter(final DatapathId dpid, final long bandwidth, final long burstSize,
                                              final long meterId) throws SwitchOperationException;

    /**
     * Deletes the meter from the switch OF_13.
     *
     * @param dpid    datapath ID of the switch
     * @param meterId meter identifier
     * @return transaction id
     * @throws SwitchOperationException Switch not found
     */
    long deleteMeter(final DatapathId dpid, final long meterId) throws SwitchOperationException;


    Map<DatapathId, IOFSwitch> getAllSwitchMap();

    /**
     * Deletes all non-default rules from the switch.
     *
     * @param dpid datapath ID of the switch
     * @return the list of cookies for removed rules
     * @throws SwitchOperationException Switch not found
     */
    List<Long> deleteAllNonDefaultRules(DatapathId dpid) throws SwitchOperationException;

    /**
     * Deletes the default rules (drop + verification) from the switch.
     *
     * @param dpid datapath ID of the switch
     * @return the list of cookies for removed rules
     * @throws SwitchOperationException Switch not found
     */
    List<Long> deleteDefaultRules(DatapathId dpid) throws SwitchOperationException;

    /**
     * Delete rules that match the criteria.
     *
     * @param dpid datapath ID of the switch
     * @param criteria the list of delete criteria
     * @return the list of removed cookies
     * @throws SwitchOperationException Switch not found
     */
    List<Long> deleteRulesByCriteria(DatapathId dpid, DeleteRulesCriteria... criteria) throws SwitchOperationException;

    /**
     * Safely install default rules - ie monitor traffic.
     */
    void startSafeMode(final DatapathId dpid) throws SwitchOperationException;

    /**
     * Stop the safe install .. switch is deactivated or removed.
     */
    void stopSafeMode(final DatapathId dpid);

    void safeModeTick();

    void sendSwitchActivate(final IOFSwitch sw) throws SwitchOperationException;

    void sendPortUpEvents(final IOFSwitch sw) throws SwitchOperationException;

    /**
     * Configure switch port. <br>
     * Configurations
     * <ul>
     * <li> UP/DOWN port </li>
     * <li> Change port speed </li>
     * </ul>
     *
     * @param dpId datapath ID of the switch.
     * @param portNumber the port to configure.
     * @param portAdminDown the port status to be applied.
     * @throws SwitchOperationException Switch not found or Port not found
     */
    void configurePort(DatapathId dpId, int portNumber, Boolean portAdminDown) throws SwitchOperationException;
}
