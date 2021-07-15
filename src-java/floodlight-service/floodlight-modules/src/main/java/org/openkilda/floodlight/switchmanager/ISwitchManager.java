/* Copyright 2018 Telstra Open Source
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

import org.openkilda.floodlight.error.InvalidMeterIdException;
import org.openkilda.floodlight.error.OfInstallException;
import org.openkilda.floodlight.error.SwitchNotFoundException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.messaging.command.flow.RuleType;
import org.openkilda.messaging.command.switches.ConnectModeRequest;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.info.meter.MeterEntry;
import org.openkilda.model.MacAddress;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSharedSegmentCookie;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFGroupDescStatsEntry;
import org.projectfloodlight.openflow.protocol.OFMeterConfig;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;

import java.net.InetAddress;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ISwitchManager extends IFloodlightService {

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
    List<Long> installDefaultRules(final DatapathId dpid) throws SwitchOperationException;

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
    long installEgressIslVxlanRule(final DatapathId dpid, int port) throws SwitchOperationException;

    /**
     * Remove intermediate rule for isl on switch in table 0 to route egress in case of vxlan.
     *
     * @param dpid datapathId of the switch
     * @param port isl port
     * @throws SwitchOperationException Switch not found
     */
    long removeEgressIslVxlanRule(final DatapathId dpid, int port) throws SwitchOperationException;

    /**
     * Install intermediate rule for isl on switch in table 0 to route transit in case of vxlan.
     *
     * @param dpid datapathId of the switch
     * @param port isl port
     * @throws SwitchOperationException Switch not found
     */
    long installTransitIslVxlanRule(final DatapathId dpid, int port) throws SwitchOperationException;

    /**
     * Remove intermediate rule for isl on switch in table 0 to route transit in case of vxlan.
     *
     * @param dpid datapathId of the switch
     * @param port isl port
     * @throws SwitchOperationException Switch not found
     */
    long removeTransitIslVxlanRule(final DatapathId dpid, int port) throws SwitchOperationException;

    /**
     * Install intermediate rule for isl on switch in table 0 to route egress in case of vlan.
     *
     * @param dpid datapathId of the switch
     * @param port isl port
     * @throws SwitchOperationException Switch not found
     */
    long installEgressIslVlanRule(final DatapathId dpid, int port) throws SwitchOperationException;

    /**
     * Install LLDP rule which will send LLDP packet from ISL port to controller.
     *
     * @param dpid datapathId of the switch
     * @throws SwitchOperationException Switch not found
     */
    Long installLldpTransitFlow(DatapathId dpid) throws SwitchOperationException;

    /**
     * Install LLDP rule which will send all LLDP packets received from not ISL/Customer ports to controller.
     *
     * @param dpid datapathId of the switch
     * @throws SwitchOperationException Switch not found
     */
    Long installLldpInputPreDropFlow(DatapathId dpid) throws SwitchOperationException;

    /**
     * Install ARP rule which will send ARP packet from ISL port to controller.
     *
     * @param dpid datapathId of the switch
     * @throws SwitchOperationException Switch not found
     */
    Long installArpTransitFlow(DatapathId dpid) throws SwitchOperationException;

    /**
     * Install ARP rule which will send all ARP packets received from not ISL/Customer ports to controller.
     *
     * @param dpid datapathId of the switch
     * @throws SwitchOperationException Switch not found
     */
    Long installArpInputPreDropFlow(DatapathId dpid) throws SwitchOperationException;

    /**
     * Install Server 42 input rule which will send Ping packet into pre ingress table.
     *
     * @param dpid datapathId of the switch
     * @param server42Port server 42 port
     * @param customerPort rule marks Ping packet by metadata to emulate that packet was received from customer port
     * @param server42MacAddress server 42 mac adress
     * @throws SwitchOperationException Switch not found
     */
    Long installServer42FlowRttInputFlow(DatapathId dpid, int server42Port, int customerPort,
                                         MacAddress server42MacAddress) throws SwitchOperationException;

    /**
     * Install Server 42 turning rule which will send Ping packet from last switch back to first.
     *
     * @param dpid datapathId of the switch
     * @throws SwitchOperationException Switch not found
     */
    Long installServer42FlowRttTurningFlow(DatapathId dpid) throws SwitchOperationException;

    /**
     * Install Server 42 output vlan rule which will send Ping packet back to Server 42.
     *
     * @param dpid datapathId of the switch
     * @param port server 42 port
     * @param vlan server 42 vlan. If vlan > 0 rule must push this vlan
     * @param macAddress server 42 mac address
     * @throws SwitchOperationException Switch not found
     */
    Long installServer42FlowRttOutputVlanFlow(DatapathId dpid, int port, int vlan, MacAddress macAddress)
            throws SwitchOperationException;

    /**
     * Install Server 42 output VXLAN rule which will send Ping packet back to Server 42.
     *
     * @param dpid datapathId of the switch
     * @param port server 42 port
     * @param vlan server 42 vlan. If vlan > 0 rule must push this vlan
     * @param macAddress server 42 mac address
     * @throws SwitchOperationException Switch not found
     */
    Long installServer42FlowRttOutputVxlanFlow(DatapathId dpid, int port, int vlan, MacAddress macAddress)
            throws SwitchOperationException;


    /**
     * Install the Server 42 ISL RTT input rule which forwards a Ping packet into the ISL port.
     *
     * @param dpid datapathId of the switch
     * @param server42Port server 42 port
     * @param islPort rule forwards Ping packet to the port
     * @throws SwitchOperationException Switch not found
     */
    Long installServer42IslRttInputFlow(DatapathId dpid, int server42Port, int islPort) throws SwitchOperationException;

    /**
     * Install the Server 42 ISL RTT turning rule which sends a Ping packet from the opposite switch back to the origin.
     *
     * @param dpid datapathId of the switch
     * @throws SwitchOperationException Switch not found
     */
    Long installServer42IslRttTurningFlow(DatapathId dpid) throws SwitchOperationException;

    /**
     * Install the Server 42 ISL RTT output rule which sends a Ping packet back to Server 42.
     *
     * @param dpid datapathId of the switch
     * @param server42Port server 42 port
     * @param server42Vlan server 42 vlan. If vlan > 0 rule must push this vlan
     * @param server42MacAddress server 42 mac address
     * @throws SwitchOperationException Switch not found
     */
    Long installServer42IslRttOutputFlow(DatapathId dpid, int server42Port, int server42Vlan,
                                         MacAddress server42MacAddress)
            throws SwitchOperationException;

    /**
     * Remove intermediate rule for isl on switch in table 0 to route egress in case of vlan.
     *
     * @param dpid datapathId of the switch
     * @param port isl port
     * @throws SwitchOperationException Switch not found
     */
    long removeEgressIslVlanRule(final DatapathId dpid, int port) throws SwitchOperationException;


    /**
     * Install intermediate rule for isl on switch in table 0 to route ingress traffic.
     *
     * @param dpid datapathId of the switch
     * @param port customer port
     * @throws SwitchOperationException Switch not found
     */
    long installIntermediateIngressRule(final DatapathId dpid, int port) throws SwitchOperationException;

    /**
     * Install input LLDP rule which will mark all LLDP packets received from Customer ports by metadata flag.
     *
     * @param dpid datapathId of the switch
     * @param port customer port
     * @throws SwitchOperationException Switch not found
     */
    long installLldpInputCustomerFlow(DatapathId dpid, int port) throws SwitchOperationException;

    /**
     * Install ingress LLDP rule which will send LLDP packets received from Customer ports to controller.
     *
     * @param dpid datapathId of the switch
     * @throws SwitchOperationException Switch not found
     */
    Long installLldpIngressFlow(DatapathId dpid) throws SwitchOperationException;

    /**
     * Install post ingress LLDP rule which will send LLDP packets received from Customer ports to controller.
     *
     * @param dpid datapathId of the switch
     * @throws SwitchOperationException Switch not found
     */
    Long installLldpPostIngressFlow(DatapathId dpid) throws SwitchOperationException;

    /**
     * Install post ingress LLDP rule which will send LLDP packets with encapsulation to controller.
     *
     * @param dpid datapathId of the switch
     * @throws SwitchOperationException Switch not found
     */
    Long installLldpPostIngressVxlanFlow(DatapathId dpid) throws SwitchOperationException;

    /**
     * Install post ingress LLDP rule which will send LLDP packets received from one switch flows to controller.
     *
     * @param dpid datapathId of the switch
     * @throws SwitchOperationException Switch not found
     */
    Long installLldpPostIngressOneSwitchFlow(DatapathId dpid) throws SwitchOperationException;

    /**
     * Install input ARP rule which will mark all ARP packets received from Customer ports by metadata flag.
     *
     * @param dpid datapathId of the switch
     * @param port customer port
     * @throws SwitchOperationException Switch not found
     */
    Long installArpInputCustomerFlow(DatapathId dpid, int port) throws SwitchOperationException;

    /**
     * Install ingress ARP rule which will send ARP packets received from Customer ports to controller.
     *
     * @param dpid datapathId of the switch
     * @throws SwitchOperationException Switch not found
     */
    Long installArpIngressFlow(DatapathId dpid) throws SwitchOperationException;

    /**
     * Install post ingress ARP rule which will send ARP packets received from Customer ports to controller.
     *
     * @param dpid datapathId of the switch
     * @throws SwitchOperationException Switch not found
     */
    Long installArpPostIngressFlow(DatapathId dpid) throws SwitchOperationException;

    /**
     * Install post ingress ARP rule which will send ARP packets with encapsulation to controller.
     *
     * @param dpid datapathId of the switch
     * @throws SwitchOperationException Switch not found
     */
    Long installArpPostIngressVxlanFlow(DatapathId dpid) throws SwitchOperationException;

    /**
     * Install post ingress ARP rule which will send ARP packets received from one switch flows to controller.
     *
     * @param dpid datapathId of the switch
     * @throws SwitchOperationException Switch not found
     */
    Long installArpPostIngressOneSwitchFlow(DatapathId dpid) throws SwitchOperationException;

    /**
     * Remove intermediate rule for isl on switch in table 0 to route ingress traffic.
     *
     * @param dpid datapathId of the switch
     * @param port customer port
     * @throws SwitchOperationException Switch not found
     */
    long removeIntermediateIngressRule(final DatapathId dpid, int port) throws SwitchOperationException;

    /**
     * Remove LLDP rule which marks LLDP packet received from customer port by metadata.
     *
     * @param dpid datapathId of the switch
     * @param port customer port
     * @throws SwitchOperationException Switch not found
     */
    long removeLldpInputCustomerFlow(DatapathId dpid, int port) throws SwitchOperationException;

    /**
     * Remove ARP rule which marks ARP packet received from customer port by metadata.
     *
     * @param dpid datapathId of the switch
     * @param port customer port
     * @throws SwitchOperationException Switch not found
     */
    Long removeArpInputCustomerFlow(DatapathId dpid, int port) throws SwitchOperationException;

    /**
     * Remove Server 42 input rule which writes customer port into metadata for ping packets received from server 42.
     *
     * @param dpid datapathId of the switch
     * @param port customer port
     * @throws SwitchOperationException Switch not found
     */
    Long removeServer42FlowRttInputFlow(DatapathId dpid, int port) throws SwitchOperationException;

    /**
     * Remove the Server 42 ISL RTT input rule which forwards a Ping packet into the ISL port.
     *
     * @param dpid datapathId of the switch
     * @param islPort ISL port
     * @throws SwitchOperationException Switch not found
     */
    Long removeServer42IslRttInputFlow(DatapathId dpid, int islPort) throws SwitchOperationException;

    /**
     * Build intermidiate flowmod for ingress rule.
     *
     * @param dpid switch id
     * @param port port
     * @return modification command
     */
    OFFlowMod buildIntermediateIngressRule(DatapathId dpid, int port) throws SwitchNotFoundException;

    /**
     * Build LLDP rule which will mark LLDP packet received from customer port by metadata.
     *
     * @param dpid switch id
     * @param port customer port
     * @return modification command
     */
    OFFlowMod buildLldpInputCustomerFlow(DatapathId dpid, int port) throws SwitchNotFoundException;

    /**
     * Build ARP rule which will mark ARP packet received from customer port by metadata.
     *
     * @param dpid switch id
     * @param port customer port
     * @return modification command
     */
    OFFlowMod buildArpInputCustomerFlow(DatapathId dpid, int port) throws SwitchNotFoundException;

    /**
     * Build all expected Server 42 Flow RTT rules.
     *
     * @param dpid switch id
     * @param server42FlowRttFeatureToggle server 42 feature toggle
     * @param server42FlowRttSwitchProperty server 42 switch property
     * @param server42Port server 42 port. Could be null if server42FlowRttSwitchProperty is false
     * @param server42Vlan vlan of packer received from server 42.
     *                     Could be null if server42FlowRttSwitchProperty is false
     * @param server42MacAddress mac address of server 42. Could be null if server42FlowRttSwitchProperty is false
     * @param customerPorts switch ports with enabled server 42 ping
     * @return modification command
     */
    List<OFFlowMod> buildExpectedServer42FlowRttFlows(
            DatapathId dpid, boolean server42FlowRttFeatureToggle, boolean server42FlowRttSwitchProperty,
            Integer server42Port, Integer server42Vlan, MacAddress server42MacAddress, Set<Integer> customerPorts)
            throws SwitchNotFoundException;

    /**
     * Build all expected Server 42 ISL RTT rules.
     *
     * @param dpid datapathId of the switch
     * @param server42IslRtt server 42 ISL RTT toggle
     * @param server42Port server 42 port. Could be null if server42IslRttSwitchProperty is false
     * @param server42Vlan vlan of a packet received from server 42.
     *                     Could be null if server42FlowRttSwitchProperty is false
     * @param server42MacAddress mac address of server 42. Could be null if server42IslRttSwitchProperty is false
     * @param islPorts switch ports with enabled server 42 ping
     * @return modification command
     */
    List<OFFlowMod> buildExpectedServer42IslRttFlows(
            DatapathId dpid, boolean server42IslRtt,
            Integer server42Port, Integer server42Vlan, org.openkilda.model.MacAddress server42MacAddress,
            Collection<Integer> islPorts) throws SwitchNotFoundException;

    /**
     * Install default pass through rule for pre ingress table.
     *
     * @param dpid datapathId of the switch
     * @return cookie id
     * @throws SwitchOperationException Switch not found
     */
    Long installPreIngressTablePassThroughDefaultRule(final DatapathId dpid) throws SwitchOperationException;

    /**
     * Install default pass through rule for pre egress table.
     *
     * @param dpid datapathId of the switch
     * @return cookie id
     * @throws SwitchOperationException Switch not found
     */
    Long installEgressTablePassThroughDefaultRule(final DatapathId dpid) throws SwitchOperationException;

    /**
     * Install isl rules for switch endpoint.
     *
     * @param dpid datapathId of the switch
     * @param port target port
     * @throws SwitchOperationException Switch not found
     */
    List<Long> installMultitableEndpointIslRules(final DatapathId dpid, final int port) throws SwitchOperationException;

    /**
     * Remove isl rules for switch endpoint.
     *
     * @param dpid datapathId of the switch
     * @param port target port
     * @throws SwitchOperationException Switch not found
     */
    List<Long> removeMultitableEndpointIslRules(final DatapathId dpid, final int port) throws SwitchOperationException;

    void installOuterVlanMatchSharedFlow(SwitchId switchId, String flowId, FlowSharedSegmentCookie cookie)
            throws SwitchOperationException;

    void installServer42OuterVlanMatchSharedFlow(DatapathId switchId, FlowSharedSegmentCookie cookie)
            throws SwitchOperationException;

    /**
     * Returns list of default flows that must be installed on a switch.
     *
     * @param dpid switch id.
     * @param multiTable flag
     * @param switchLldp flag. True means that switch must has rules for catching LLDP packets.
     * @param switchArp flag. True means that switch must have rules for catching ARP packets.
     * @return list of default flows.
     */
    List<OFFlowMod> getExpectedDefaultFlows(
            DatapathId dpid, boolean multiTable, boolean switchLldp, boolean switchArp) throws SwitchOperationException;

    /**
     * Returns list of default meters that must be installed on a switch.
     *
     * @param dpid switch id.
     * @param multiTable flag
     * @param switchLldp flag. True means that switch must has rules for catching LLDP packets.
     * @param switchArp flag. True means that switch must have rules for catching ARP packets.
     * @return list of default meters.
     */
    List<MeterEntry> getExpectedDefaultMeters(
            DatapathId dpid, boolean multiTable, boolean switchLldp, boolean switchArp) throws SwitchOperationException;

    /**
     * Returns list of flows that must be installed for multitable pipeline per isl port.
     *
     * @param dpid switch id
     * @param port isl port
     * @return list of isl flows
     */
    List<OFFlowMod> getExpectedIslFlowsForPort(DatapathId dpid, int port) throws SwitchOperationException;

    /**
     * Returns list of groups installed on switch.
     * @param dpid switch id
     * @return list of groups
     */
    List<OFGroupDescStatsEntry> dumpGroups(DatapathId dpid) throws SwitchOperationException;

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
     * Updates a meter on ingress switch OF_13.
     *
     * @param dpid datapath ID of the switch
     * @param meterId the meter ID
     * @param bandwidth the bandwidth
     * @throws SwitchOperationException Switch not found
     */
    void modifyMeterForFlow(DatapathId dpid, long meterId, long bandwidth) throws SwitchOperationException;

    /**
     * Modifies default meter.
     *
     * @param dpid datapath ID of the switch
     * @param meterId the meter ID
     * @throws SwitchNotFoundException Switch not found
     * @throws InvalidMeterIdException Meter ID is invalid
     * @throws UnsupportedSwitchOperationException Switch doesn't support meters
     * @throws OfInstallException Error during meter modification
     */
    void modifyDefaultMeter(DatapathId dpid, long meterId) throws SwitchNotFoundException, InvalidMeterIdException,
            UnsupportedSwitchOperationException, OfInstallException;

    /**
     * Deletes the meter from the switch OF_13.
     *
     * @param dpid datapath ID of the switch
     * @param meterId meter identifier
     * @throws SwitchOperationException Switch not found
     */
    void deleteMeter(final DatapathId dpid, final long meterId) throws SwitchOperationException;


    Map<DatapathId, IOFSwitch> getAllSwitchMap(boolean visible);

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
     * @param islPorts ports with isl default rule
     * @param flowPorts ports with flow default rule
     * @param flowLldpPorts ports with lldp flow default rule
     * @param flowArpPorts ports with arp flow default rule
     * @param server42FlowRttPorts ports server 42 input flow default rule
     * @param multiTable multiTableMode
     * @param switchLldp switch Lldp enabled. True means that switch has rules for catching LLDP packets.
     * @param switchArp switch Arp enabled. True means that switch has rules for catching ARP packets.
     * @param server42FlowRtt server 42 flow RTT. True means that switch has rules for pinging Flows from server 42.
     * @param server42IslRtt server 42 ISL RTT. True means that switch has rules for pinging ISLs from server 42.
     * @return the list of cookies for removed rules
     * @throws SwitchOperationException Switch not found
     */
    List<Long> deleteDefaultRules(DatapathId dpid, List<Integer> islPorts,
                                  List<Integer> flowPorts, Set<Integer> flowLldpPorts, Set<Integer> flowArpPorts,
                                  Set<Integer> server42FlowRttPorts, boolean multiTable, boolean switchLldp,
                                  boolean switchArp, boolean server42FlowRtt, boolean server42IslRtt)
            throws SwitchOperationException;

    /**
     * Delete rules that match the criteria.
     *
     * @param dpid datapath ID of the switch
     * @param criteria the list of delete criteria
     * @param multiTable flag
     * @param ruleType rule type
     * @return the list of removed cookies
     * @throws SwitchOperationException Switch not found
     */
    List<Long> deleteRulesByCriteria(DatapathId dpid, boolean multiTable, RuleType ruleType,
                                     DeleteRulesCriteria... criteria) throws SwitchOperationException;

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
     * Return switch manager config.
     *
     * @return SwitchManagerConfig.
     */
    SwitchManagerConfig getSwitchManagerConfig();
}
