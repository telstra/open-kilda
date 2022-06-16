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

import org.openkilda.floodlight.error.SwitchNotFoundException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFGroupDescStatsEntry;
import org.projectfloodlight.openflow.protocol.OFMeterConfig;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

public interface ISwitchManager extends IFloodlightService {

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
     * Install the Server 42 ISL RTT input rule which forwards a Ping packet into the ISL port.
     *
     * @param dpid datapathId of the switch
     * @param server42Port server 42 port
     * @param islPort rule forwards Ping packet to the port
     * @throws SwitchOperationException Switch not found
     */
    Long installServer42IslRttInputFlow(DatapathId dpid, int server42Port, int islPort) throws SwitchOperationException;

    /**
     * Remove intermediate rule for isl on switch in table 0 to route egress in case of vlan.
     *
     * @param dpid datapathId of the switch
     * @param port isl port
     * @throws SwitchOperationException Switch not found
     */
    long removeEgressIslVlanRule(final DatapathId dpid, int port) throws SwitchOperationException;


    /**
     * Remove the Server 42 ISL RTT input rule which forwards a Ping packet into the ISL port.
     *
     * @param dpid datapathId of the switch
     * @param islPort ISL port
     * @throws SwitchOperationException Switch not found
     */
    Long removeServer42IslRttInputFlow(DatapathId dpid, int islPort) throws SwitchOperationException;

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
     * Delete rules that match the criteria.
     *
     * @param dpid datapath ID of the switch
     * @param criteria the list of delete criteria
     * @return the list of removed cookies
     * @throws SwitchOperationException Switch not found
     */
    List<Long> deleteRulesByCriteria(DatapathId dpid, DeleteRulesCriteria... criteria) throws SwitchOperationException;

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
