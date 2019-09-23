/* Copyright 2019 Telstra Open Source
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

import org.openkilda.floodlight.error.SwitchNotFoundException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.messaging.command.switches.ConnectModeRequest;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.model.FlowApplication;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.OutputVlanType;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFMeterConfig;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ISwitchManager extends IFloodlightService {
    /**
     * OVS software switch manufacturer constant value.
     */
    String OVS_MANUFACTURER = "Nicira, Inc.";

    long COOKIE_FLAG_SERVICE = 0x8000000000000000L;
    long COOKIE_FLAG_BFD_CATCH = 0x0001000000000001L;

    void activate(DatapathId dpid) throws SwitchOperationException;

    void deactivate(DatapathId dpid);

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
     * Add default rule for install verfication rule applicable for vxlan.
     *
     * @param dpid datapathId of switch
     * @return unicast verification vxlan rule cookie if installation succeeds otherwise null.
     * @throws SwitchOperationException in case of errors
     */
    Long installUnicastVerificationRuleVxlan(final DatapathId dpid) throws SwitchOperationException;

    /**
     * Installs the default verification rule, if it is allowed. One case where it isn't -
     * if the switch is an OpenFlow 1.2 switch and isBroadcast = false. In that scenario, nothing
     * happens.
     *
     * @return verification rule cookie if installation succeeds otherwise null.
     */
    Long installVerificationRule(final DatapathId dpid, final boolean isBroadcast)
            throws SwitchOperationException;

    /**
     * Installs the default drop rule.
     *
     * @param dpid datapathId of switch
     * @param tableId target table id
     * @param cookie drop rule cookie
     * @throws SwitchOperationException in case of errors
     */
    Long installDropFlowForTable(final DatapathId dpid, final int tableId,
                                 final long cookie) throws SwitchOperationException;

    /**
     * Installs the default drop rule.
     *
     * @param dpid datapathId of switch
     * @return drop rule cookie if installation succeeds otherwise null.
     * @throws SwitchOperationException in case of errors
     */
    Long installDropFlow(final DatapathId dpid) throws SwitchOperationException;

    /**
     * Installs the drop verification loop rule.
     *
     * @param dpid datapathId of switch
     * @return drop loop rule cookie if installation succeeds otherwise null.
     * @throws SwitchOperationException in case of errors
     */
    Long installDropLoopRule(final DatapathId dpid) throws SwitchOperationException;

    /**
     * Installs the default catch rule for BFD. Only applicable for NoviFlow switches.
     *
     * @param dpid datapathId of switch
     * @return bfd catch rule cookie if installation succeeds otherwise null.
     * @throws SwitchOperationException in case of errors
     */
    Long installBfdCatchFlow(final DatapathId dpid) throws SwitchOperationException;

    /**
     * Installs the default round trip latency rule. Only applicable for NoviFlow switches.
     *
     * @param dpid datapathId of switch
     * @return round trip latency rule cookie if installation succeeds otherwise null.
     * @throws SwitchOperationException in case of errors
     */
    Long installRoundTripLatencyFlow(final DatapathId dpid) throws SwitchOperationException;

    /**
     * Install intermediate rule for isl on switch in table 0 to route egress in case of vxlan.
     *
     * @param dpid datapathId of the switch
     * @param port isl port
     * @throws SwitchOperationException Switch not found
     */
    void installEgressIslVxlanRule(final DatapathId dpid, int port) throws SwitchOperationException;

    /**
     * Install intermediate rule for isl on switch in table 0 to route transit in case of vxlan.
     *
     * @param dpid datapathId of the switch
     * @param port isl port
     * @throws SwitchOperationException Switch not found
     */
    void installTransitIslVxlanRule(final DatapathId dpid, int port) throws SwitchOperationException;

    /**
     * Install intermediate rule for isl on switch in table 0 to route egress in case of vlan.
     *
     * @param dpid datapathId of the switch
     * @param port isl port
     * @throws SwitchOperationException Switch not found
     */
    void installEgressIslVlanRule(final DatapathId dpid, int port) throws SwitchOperationException;

    /**
     * Install intermediate rule for isl on switch in table 0 to route ingress traffic.
     *
     * @param dpid datapathId of the switch
     * @param port customer port
     * @throws SwitchOperationException Switch not found
     */
    void installIntermediateIngressRule(final DatapathId dpid, int port) throws SwitchOperationException;

    /**
     * Install default pass through rule for pre ingress table.
     *
     * @param dpid datapathId of the switch
     * @throws SwitchOperationException Switch not found
     */
    void installPreIngressTablePassThroughDefaultRule(final DatapathId dpid) throws SwitchOperationException;

    /**
     * Install default pass through rule for pre egress table.
     *
     * @param dpid datapathId of the switch
     * @throws SwitchOperationException Switch not found
     */
    void installEgressTablePassThroughDefaultRule(final DatapathId dpid) throws SwitchOperationException;

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
     * @param dpid datapathId of the switch
     * @param dstDpid datapathId of the egress switch
     * @param flowId flow id
     * @param inputPort port to expect the packet on
     * @param outputPort port to forward the packet out
     * @param inputVlanId input vlan to match on, 0 means not to match on vlan
     * @param transitTunnelId vlan or vni to add before outputing on outputPort
     * @param encapsulationType flow encapsulation type
     * @param enableLldp        if True LLDP packets will be send to LLDP rule
     * @param multiTable multitable pipeline flag
     * @param applications flow applications
     * @return transaction id
     * @throws SwitchOperationException Switch not found
     */
    long installIngressFlow(DatapathId dpid, DatapathId dstDpid, String flowId, Long cookie, int inputPort,
                            int outputPort, int inputVlanId,
                            int transitTunnelId, OutputVlanType outputVlanType, long meterId,
                            FlowEncapsulationType encapsulationType, boolean enableLldp, boolean multiTable,
                            Set<FlowApplication> applications)
            throws SwitchOperationException;

    /**
     * Installs a flow to catch LLDP packets.
     *
     * @param dpid              datapathId of the switch
     * @param inputPort         port to expect the packet on
     * @param tunnelId          vlan or vni to match packet
     * @param encapsulationType flow encapsulation type
     * @param multiTable        switch operations mode
     * @return transaction id
     * @throws SwitchOperationException Switch not found
     */
    long installLldpIngressFlow(DatapathId dpid, Long cookie, int inputPort, int tunnelId, long meterId,
                                FlowEncapsulationType encapsulationType, boolean multiTable)
            throws SwitchOperationException;

    /**
     * Installs flow on egress swtich.
     *
     * @param dpid datapathId of the switch
     * @param flowId flow id
     * @param inputPort port to expect the packet on
     * @param outputPort port to forward the packet out
     * @param transitTunnelId vlan or vni to match on the ingressPort
     * @param outputVlanId set vlan on packet before forwarding via outputPort; 0 means not to set
     * @param outputVlanType type of action to apply to the outputVlanId if greater than 0
     * @param encapsulationType flow encapsulation type
     * @param multiTable multitable pipeline flag
     * @return transaction id
     * @throws SwitchOperationException Switch not found
     */
    long installEgressFlow(DatapathId dpid, String flowId, Long cookie, int inputPort, int outputPort,
                           int transitTunnelId, int outputVlanId, OutputVlanType outputVlanType,
                           FlowEncapsulationType encapsulationType,
                           boolean multiTable)
            throws SwitchOperationException;

    /**
     * Installs flow on a transit switch.
     *
     * @param dpid datapathId of the switch
     * @param flowId flow id
     * @param inputPort port to expect packet on
     * @param outputPort port to forward packet out
     * @param transitTunnelId vlan or vni to match on inputPort
     * @param encapsulationType flow encapsulation type
     * @param multiTable multitable pipeline flag
     * @return transaction id
     * @throws SwitchOperationException Switch not found
     */
    long installTransitFlow(DatapathId dpid, String flowId, Long cookie, int inputPort, int outputPort,
                            int transitTunnelId, FlowEncapsulationType encapsulationType, boolean multiTable)
            throws SwitchOperationException;

    /**
     * Installs flow through one switch.
     *
     * @param dpid datapathId of the switch
     * @param flowId flow id
     * @param inputPort port to expect packet on
     * @param outputPort port to forward packet out
     * @param inputVlanId vlan to match on inputPort
     * @param outputVlanId set vlan on packet before forwarding via outputPort; 0 means not to set
     * @param outputVlanType type of action to apply to the outputVlanId if greater than 0
     * @param enableLldp     if True LLDP packets will be send to LLDP rule
     * @param multiTable multitable pipeline flag
     * @return transaction id
     * @throws SwitchOperationException Switch not found
     */
    long installOneSwitchFlow(final DatapathId dpid, final String flowId, final Long cookie,
                                                      final int inputPort, final int outputPort, int inputVlanId,
                                                      int outputVlanId, final OutputVlanType outputVlanType,
                                                      final long meterId, boolean enableLldp, boolean multiTable)
            throws SwitchOperationException;

    /**
     * Returns list of default flows that must be installed on a switch.
     *
     * @param dpid switch id.
     * @return list of default flows.
     */
    List<OFFlowMod> getExpectedDefaultFlows(DatapathId dpid) throws SwitchOperationException;

    /**
     * Returns list of installed flows.
     *
     * @param dpid switch id
     * @return OF flow stats entries
     */
    List<OFFlowStatsEntry> dumpFlowTable(final DatapathId dpid) throws SwitchNotFoundException;

    /**
     * Returns list of installed meters.
     *
     * @param dpid switch id
     * @return OF meter config stats entries
     * @throws SwitchOperationException Switch not found
     */
    List<OFMeterConfig> dumpMeters(final DatapathId dpid) throws SwitchOperationException;

    /**
     * Returns a installed meter by id.
     *
     * @param dpid switch id
     * @param meterId a meter id
     * @return OF meter config stats entry
     * @throws SwitchOperationException Switch not found
     */
    OFMeterConfig dumpMeterById(final DatapathId dpid, final long meterId) throws SwitchOperationException;

    /**
     * Installs a meter on ingress switch OF_13.
     * TODO: describe params meaning in accordance with OF
     *
     * @param dpid datapath ID of the switch
     * @param bandwidth the bandwidth limit value
     * @param meterId the meter ID
     * @throws SwitchOperationException Switch not found
     */
    void installMeterForFlow(DatapathId dpid, long bandwidth, long meterId) throws SwitchOperationException;

    /**
     * Updates a meter on ingress switch OF_13.
     *
     * @param dpid datapath ID of the switch
     * @param meterId the meter ID
     * @param bandwidth the bandwidth
     * @throws SwitchOperationException Switch not found
     */
    void modifyMeterForFlow(DatapathId dpid, long meterId, long bandwidth) throws SwitchOperationException;

    /**
     * Deletes the meter from the switch OF_13.
     *
     * @param dpid datapath ID of the switch
     * @param meterId meter identifier
     * @throws SwitchOperationException Switch not found
     */
    void deleteMeter(final DatapathId dpid, final long meterId) throws SwitchOperationException;


    Map<DatapathId, IOFSwitch> getAllSwitchMap();

    /**
     * Wrap IOFSwitchService.getSwitch call to check protect from null return value.
     *
     * @param dpId switch identifier
     * @return open flow switch descriptor
     * @throws SwitchNotFoundException switch operation exception
     */
    IOFSwitch lookupSwitch(DatapathId dpId) throws SwitchNotFoundException;

    /**
     * Get the IP address from a switch.
     *
     * @param sw target switch object
     * @return switch's IP address
     */
    InetAddress getSwitchIpAddress(IOFSwitch sw);

    List<OFPortDesc> getEnabledPhysicalPorts(DatapathId dpid) throws SwitchNotFoundException;

    List<OFPortDesc> getPhysicalPorts(DatapathId dpid) throws SwitchNotFoundException;

    List<OFPortDesc> getPhysicalPorts(IOFSwitch sw);

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

    void safeModeTick();

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

    /**
     * Return a list of ports description.
     *
     * @param dpid switch id.
     * @return a list of ports description.
     * @throws SwitchOperationException Switch not found.
     */
    List<OFPortDesc> dumpPortsDescription(DatapathId dpid) throws SwitchOperationException;

    /**
     * Create a MAC address based on the DPID.
     *
     * @param dpId switch object
     * @return {@link MacAddress}
     */
    MacAddress dpIdToMac(final DatapathId dpId);

    /**
     * Return if tracking is enabled.
     *
     * @return true if tracking is enabled.
     */
    boolean isTrackingEnabled();

    /**
     * Install exclusion.
     *
     * @param dpid              switch id.
     * @param srcIp             source IP address.
     * @param srcPort           source port.
     * @param dstIp             destination IP address.
     * @param dstPort           destination port.
     * @param proto             IP protocol.
     * @param ethType           Ethernet type.
     * @param transitTunnelId   transit tunnel id. Used to match by flow.
     * @return transaction id.
     * @throws SwitchOperationException Switch not found.
     */
    long installExclusion(DatapathId dpid, IPv4Address srcIp, int srcPort, IPv4Address dstIp, int dstPort,
                          IpProtocol proto, EthType ethType, int transitTunnelId)
            throws SwitchOperationException;

    /**
     * Remove exclusion.
     *
     * @param dpid              switch id.
     * @param srcIp             source IP address.
     * @param srcPort           source port.
     * @param dstIp             destination IP address.
     * @param dstPort           destination port.
     * @param proto             IP protocol.
     * @param ethType           Ethernet type.
     * @param transitTunnelId   transit tunnel id. Used to match by flow.
     * @return transaction id.
     * @throws SwitchOperationException Switch not found.
     */
    long removeExclusion(DatapathId dpid, IPv4Address srcIp, int srcPort, IPv4Address dstIp, int dstPort,
                         IpProtocol proto, EthType ethType, int transitTunnelId)
            throws SwitchOperationException;
}
