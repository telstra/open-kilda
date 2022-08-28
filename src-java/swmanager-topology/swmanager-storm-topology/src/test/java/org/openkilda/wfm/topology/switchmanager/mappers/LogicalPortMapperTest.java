/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.switchmanager.mappers;

import static org.junit.Assert.assertEquals;
import static org.openkilda.wfm.topology.switchmanager.mappers.LogicalPortMapper.INSTANCE;

import org.openkilda.messaging.info.switches.v2.LogicalPortInfoEntryV2;
import org.openkilda.messaging.model.grpc.LogicalPort;
import org.openkilda.messaging.model.grpc.LogicalPortType;
import org.openkilda.model.LagLogicalPort;
import org.openkilda.model.SwitchId;

import com.google.common.collect.Lists;
import org.junit.Test;

public class LogicalPortMapperTest {
    public static SwitchId SWITCH_ID = new SwitchId(1);
    public static int LAG_PORT = 2;
    public static int PHYSICAL_PORT_1 = 3;
    public static int PHYSICAL_PORT_2 = 4;

    @Test
    public void mapLagLogicalPortTest() {
        LagLogicalPort lagLogicalPort = new LagLogicalPort(SWITCH_ID, LAG_PORT,
                Lists.newArrayList(PHYSICAL_PORT_1, PHYSICAL_PORT_2));

        LogicalPortInfoEntryV2 port = INSTANCE.map(lagLogicalPort);
        assertEquals(LAG_PORT, port.getLogicalPortNumber().intValue());
        assertEquals(2, port.getPhysicalPorts().size());
        assertEquals(PHYSICAL_PORT_1, port.getPhysicalPorts().get(0).intValue());
        assertEquals(PHYSICAL_PORT_2, port.getPhysicalPorts().get(1).intValue());
    }

    @Test
    public void mapLogicalPortTest() {
        LogicalPort logicalPort = LogicalPort.builder()
                .logicalPortNumber(LAG_PORT)
                .portNumbers(Lists.newArrayList(PHYSICAL_PORT_1, PHYSICAL_PORT_2))
                .type(LogicalPortType.LAG)
                .name("some")
                .build();

        LogicalPortInfoEntryV2 port = INSTANCE.map(logicalPort);
        assertEquals(LAG_PORT, port.getLogicalPortNumber().intValue());
        assertEquals(logicalPort.getType().name(), port.getType().getType());
        assertEquals(logicalPort.getPortNumbers().size(), port.getPhysicalPorts().size());
        assertEquals(PHYSICAL_PORT_1, port.getPhysicalPorts().get(0).intValue());
        assertEquals(PHYSICAL_PORT_2, port.getPhysicalPorts().get(1).intValue());
    }

    @Test
    public void mapGrpcLogicalPortType() {
        assertEquals(org.openkilda.messaging.info.switches.LogicalPortType.LAG, INSTANCE.map(LogicalPortType.LAG));
        assertEquals(org.openkilda.messaging.info.switches.LogicalPortType.BFD, INSTANCE.map(LogicalPortType.BFD));
        assertEquals(org.openkilda.messaging.info.switches.LogicalPortType.RESERVED,
                INSTANCE.map(LogicalPortType.RESERVED));
    }

    @Test
    public void mapMessagingLogicalPortType() {
        assertEquals(LogicalPortType.LAG, INSTANCE.map(org.openkilda.messaging.info.switches.LogicalPortType.LAG));
        assertEquals(LogicalPortType.BFD, INSTANCE.map(org.openkilda.messaging.info.switches.LogicalPortType.BFD));
        assertEquals(LogicalPortType.RESERVED,
                INSTANCE.map(org.openkilda.messaging.info.switches.LogicalPortType.RESERVED));
    }
}
