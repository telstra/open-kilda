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

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.openkilda.messaging.payload.flow.OutputVlanType;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsReply;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.List;
import java.util.Map;

/**
 * Created by jonv on 29/3/17.
 */
public interface ISwitchManager extends IFloodlightService {
    /** OVS software switch manufacturer constant value. */
    String OVS_MANUFACTURER = "Nicira, Inc.";

    /**
     * Adds default rules to install verification rules and final drop rule.
     *
     * @param dpid datapathId of switch
     * @throws SwitchOperationException in case of errors
     */
    void installDefaultRules(final DatapathId dpid) throws SwitchOperationException;

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
     */
    long installOneSwitchFlow(final DatapathId dpid, final String flowId, final Long cookie,
                                                      final int inputPort, final int outputPort, int inputVlanId,
                                                      int outputVlanId, final OutputVlanType outputVlanType,
                                                      final long meterId) throws SwitchOperationException;

    /**
     * Returns list of installed flows
     *
     * @param dpid switch id
     * @return OF flow stats entries
     */
    OFFlowStatsReply dumpFlowTable(final DatapathId dpid);

    /**
     * Returns list of installed meters
     *
     * @param dpid switch id
     * @return OF meter config stats entries
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
     */
    long installMeter(final DatapathId dpid, final long bandwidth, final long burstSize,
                                              final long meterId) throws SwitchOperationException;

    /**
     * Deletes the flow from the switch
     *
     * @param dpid   datapath ID of the switch
     * @param flowId flow id
     * @return transaction id
     */
    long deleteFlow(final DatapathId dpid, final String flowId, final Long cookie)
            throws SwitchOperationException;

    /**
     * Deletes the meter from the switch OF_13.
     *
     * @param dpid    datapath ID of the switch
     * @param meterId meter identifier
     * @return transaction id
     */
    long deleteMeter(final DatapathId dpid, final long meterId) throws SwitchOperationException;


    Map<DatapathId, IOFSwitch> getAllSwitchMap();

    /**
     * Deletes all non-default rules from the switch
     *
     * @param dpid datapath ID of the switch
     * @return the list of cookies for removed rules
     */
    List<Long> deleteAllNonDefaultRules(final DatapathId dpid) throws SwitchOperationException;

    /**
     * Deletes the default rules (drop + verification) from the switch
     *
     * @param dpid datapath ID of the switch
     * @return the list of cookies for removed rules
     */
    List<Long> deleteDefaultRules(final DatapathId dpid) throws SwitchOperationException;
}
