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

package org.bitbucket.openkilda.topology;

import static java.util.Arrays.asList;
import static org.bitbucket.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.command.flow.InstallEgressFlow;
import org.bitbucket.openkilda.messaging.command.flow.InstallIngressFlow;
import org.bitbucket.openkilda.messaging.command.flow.InstallTransitFlow;
import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathNode;
import org.bitbucket.openkilda.messaging.info.event.SwitchEventType;
import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;
import org.bitbucket.openkilda.messaging.payload.flow.OutputVlanType;
import org.bitbucket.openkilda.topology.service.IslService;
import org.bitbucket.openkilda.topology.service.SwitchService;

import com.google.common.collect.Lists;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TestUtils {
    public static final String srcSwitchId = "00:00:00:00:00:01";
    public static final String firstTransitSwitchId = "00:00:00:00:00:02";
    public static final String secondTransitSwitchId = "00:00:00:00:00:03";
    public static final String dstSwitchId = "00:00:00:00:00:04";
    public static final String alternativeSwitchId = "00:00:00:00:00:05";
    public static final String address = "127.0.0.1";
    public static final String name = "localhost";
    public static final long portSpeed = 1000000;
    public static final String flowId = "test-flow";
    public static final long COOKIE = 0L;
    public static final long METER_ID = 2L;
    public static final int INPUT_VLAN_ID = 100;
    public static final int OUTPUT_VLAN_ID = 200;
    public static final int TRANSIT_VLAN_ID = 2;
    public static final int DIRECT_INCOMING_PORT = 1;
    public static final int REVERSE_OUTGOING_PORT = DIRECT_INCOMING_PORT;
    public static final int DIRECT_OUTGOING_PORT = 2;
    public static final int REVERSE_INGOING_PORT = DIRECT_OUTGOING_PORT;

    public static Set<CommandMessage> createTopology(final SwitchService switchService, final IslService islService) {
        Set<CommandMessage> commands = new HashSet<>();

        switchService.add(new SwitchInfoData(srcSwitchId,
                SwitchEventType.ADDED, address, name, "OVS 1"));
        switchService.add(new SwitchInfoData(firstTransitSwitchId,
                SwitchEventType.ADDED, address, name, "OVS 2"));
        switchService.add(new SwitchInfoData(secondTransitSwitchId,
                SwitchEventType.ADDED, address, name, "OVS 1"));
        switchService.add(new SwitchInfoData(dstSwitchId,
                SwitchEventType.ADDED, address, name, "OVS 2"));
        switchService.activate(new SwitchInfoData(srcSwitchId,
                SwitchEventType.ACTIVATED, address, name, "OVS 1"));
        switchService.activate(new SwitchInfoData(firstTransitSwitchId,
                SwitchEventType.ACTIVATED, address, name, "OVS 2"));
        switchService.activate(new SwitchInfoData(secondTransitSwitchId,
                SwitchEventType.ACTIVATED, address, name, "OVS 1"));
        switchService.activate(new SwitchInfoData(dstSwitchId,
                SwitchEventType.ACTIVATED, address, name, "OVS 2"));

        PathNode srcNode;
        PathNode dstNode;
        List<PathNode> list;

        srcNode = new PathNode(srcSwitchId, 1, 0);
        dstNode = new PathNode(firstTransitSwitchId, 2, 1);
        list = asList(srcNode, dstNode);
        islService.discoverLink(new IslInfoData(10L, list, portSpeed));
        islService.discoverLink(new IslInfoData(10L, Lists.reverse(list), portSpeed));

        srcNode = new PathNode(firstTransitSwitchId, 1, 0);
        dstNode = new PathNode(secondTransitSwitchId, 2, 1);
        list = asList(srcNode, dstNode);
        islService.discoverLink(new IslInfoData(10L, list, portSpeed));
        islService.discoverLink(new IslInfoData(10L, Lists.reverse(list), portSpeed));

        srcNode = new PathNode(secondTransitSwitchId, 1, 0);
        dstNode = new PathNode(dstSwitchId, 2, 1);
        list = asList(srcNode, dstNode);
        islService.discoverLink(new IslInfoData(10L, list, portSpeed));
        islService.discoverLink(new IslInfoData(10L, Lists.reverse(list), portSpeed));

        commands.add(new CommandMessage(new InstallIngressFlow(0L, flowId, COOKIE, srcSwitchId,
                DIRECT_INCOMING_PORT, DIRECT_OUTGOING_PORT, INPUT_VLAN_ID, TRANSIT_VLAN_ID,
                OutputVlanType.REPLACE, 10000L, 0L),
                METER_ID, DEFAULT_CORRELATION_ID, Destination.WFM));
        commands.add(new CommandMessage(new InstallTransitFlow(0L, flowId, COOKIE, firstTransitSwitchId,
                DIRECT_INCOMING_PORT, DIRECT_OUTGOING_PORT, TRANSIT_VLAN_ID),
                0L, DEFAULT_CORRELATION_ID, Destination.WFM));
        commands.add(new CommandMessage(new InstallTransitFlow(0L, flowId, COOKIE, secondTransitSwitchId,
                DIRECT_INCOMING_PORT, DIRECT_OUTGOING_PORT, TRANSIT_VLAN_ID),
                0L, DEFAULT_CORRELATION_ID, Destination.WFM));
        commands.add(new CommandMessage(new InstallEgressFlow(0L, flowId, COOKIE, dstSwitchId,
                DIRECT_INCOMING_PORT, DIRECT_OUTGOING_PORT, TRANSIT_VLAN_ID, OUTPUT_VLAN_ID, OutputVlanType.REPLACE),
                0L, DEFAULT_CORRELATION_ID, Destination.WFM));

        commands.add(new CommandMessage(new InstallIngressFlow(0L, flowId, COOKIE, srcSwitchId,
                REVERSE_INGOING_PORT, REVERSE_OUTGOING_PORT, OUTPUT_VLAN_ID, TRANSIT_VLAN_ID, OutputVlanType.REPLACE,
                10000L, 0L), METER_ID, DEFAULT_CORRELATION_ID, Destination.WFM));
        commands.add(new CommandMessage(new InstallTransitFlow(0L, flowId, COOKIE, srcSwitchId,
                REVERSE_INGOING_PORT, REVERSE_OUTGOING_PORT, TRANSIT_VLAN_ID),
                0L, DEFAULT_CORRELATION_ID, Destination.WFM));
        commands.add(new CommandMessage(new InstallTransitFlow(0L, flowId, COOKIE, srcSwitchId,
                REVERSE_INGOING_PORT, REVERSE_OUTGOING_PORT, TRANSIT_VLAN_ID),
                0L, DEFAULT_CORRELATION_ID, Destination.WFM));
        commands.add(new CommandMessage(new InstallEgressFlow(0L, flowId, COOKIE, srcSwitchId,
                REVERSE_INGOING_PORT, REVERSE_OUTGOING_PORT, 2, INPUT_VLAN_ID, OutputVlanType.REPLACE),
                0L, DEFAULT_CORRELATION_ID, Destination.WFM));

        return commands;
    }
}
