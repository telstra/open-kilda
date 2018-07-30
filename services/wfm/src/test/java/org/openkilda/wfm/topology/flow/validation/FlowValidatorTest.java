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

package org.openkilda.wfm.topology.flow.validation;

import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.pce.cache.FlowCache;
import org.openkilda.pce.provider.TopologyRepository;
import org.openkilda.wfm.topology.flow.PathComputerMock;

import org.junit.BeforeClass;
import org.junit.Test;

public class FlowValidatorTest {
    private FlowValidator target = new FlowValidator(flowCache, new PathComputerMock());

    private static final FlowCache flowCache = new FlowCache();
    private static final String SRC_SWITCH = "00:00:00:00:00:00:00:01";
    private static final int SRC_PORT = 1;
    private static final int SRC_VLAN = 1;
    private static final int SRC_VLAN_OF_EXISTING_FLOW = 2;
    private static final String DST_SWITCH = "00:00:00:00:00:00:00:02";
    private static final int DST_PORT = 5;
    private static final int DST_VLAN = 5;
    private static final int DST_VLAN_OF_EXISTING_FLOW = 6;
    private static final int ISL_PORT = 10;

    @BeforeClass
    public static void initCache() {
        Flow flow = new Flow();
        flow.setFlowId("1");
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setSourcePort(SRC_PORT);
        flow.setSourceVlan(SRC_VLAN_OF_EXISTING_FLOW);

        flow.setDestinationSwitch(DST_SWITCH);
        flow.setDestinationPort(DST_PORT);
        flow.setDestinationVlan(DST_VLAN_OF_EXISTING_FLOW);
        flowCache.pushFlow(new ImmutablePair<>(flow, flow));
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfSourceVlanIsZeroAndPortIsOccupied() throws FlowValidationException {
        // given
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setSourcePort(SRC_PORT);
        flow.setSourceVlan(0);

        // when
        target.checkFlowForEndpointConflicts(flow);

        // then a FlowValidationException is thrown
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfSourceVlanIsAlreadyOccupied() throws FlowValidationException {
        // given
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setSourcePort(SRC_PORT);
        flow.setSourceVlan(SRC_VLAN_OF_EXISTING_FLOW);

        // when
        target.checkFlowForEndpointConflicts(flow);

        // then a FlowValidationException is thrown
    }

    @Test
    public void shouldNotFailIfSourceVlanIsNotOccupied() throws FlowValidationException {
        // given
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setSourcePort(SRC_PORT);
        flow.setSourceVlan(SRC_VLAN);

        // when
        target.checkFlowForEndpointConflicts(flow);

        // then pass with no exceptions
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfDestinationVlanIsZeroAndPortIsOccupied() throws FlowValidationException {
        // given
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setSourcePort(SRC_PORT);
        flow.setSourceVlan(SRC_VLAN);
        flow.setDestinationSwitch(DST_SWITCH);
        flow.setDestinationPort(DST_PORT);
        flow.setDestinationVlan(0);

        // when
        target.checkFlowForEndpointConflicts(flow);

        // then a FlowValidationException is thrown
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfDestinationVlanIsAlreadyOccupied() throws FlowValidationException {
        // given
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setSourcePort(SRC_PORT);
        flow.setSourceVlan(SRC_VLAN);
        flow.setDestinationSwitch(DST_SWITCH);
        flow.setDestinationPort(DST_PORT);
        flow.setDestinationVlan(DST_VLAN_OF_EXISTING_FLOW);

        // when
        target.checkFlowForEndpointConflicts(flow);

        // then a FlowValidationException is thrown
    }

    @Test
    public void shouldNotFailIfDestinationVlanIsNotOccupied() throws FlowValidationException {
        // given
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setSourcePort(SRC_PORT);
        flow.setSourceVlan(SRC_VLAN);
        flow.setDestinationSwitch(DST_SWITCH);
        flow.setDestinationPort(DST_PORT);
        flow.setDestinationVlan(DST_VLAN);

        // when
        target.checkFlowForEndpointConflicts(flow);

        // then pass with no exceptions
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailForNegativeBandwidth() throws FlowValidationException {
        // given
        Flow flow = new Flow();
        flow.setBandwidth(-1);

        // when
        target.checkBandwidth(flow);

        // then a FlowValidationException is thrown
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfSourcePortIsIslPort() throws FlowValidationException {
        // given
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setSourcePort(ISL_PORT);
        flow.setSourceVlan(SRC_VLAN);

        // when
        FlowValidator target = new FlowValidator(new FlowCache(), buildTopologyRepository(SRC_SWITCH));
        target.checkFlowForIslConflicts(flow);

        // then a FlowValidationException is thrown
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfDestinationPortIsIslPort() throws FlowValidationException {
        // given
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setSourcePort(SRC_PORT);
        flow.setSourceVlan(SRC_VLAN);
        flow.setDestinationSwitch(DST_SWITCH);
        flow.setDestinationPort(ISL_PORT);
        flow.setDestinationVlan(DST_VLAN);

        // when
        FlowValidator target = new FlowValidator(new FlowCache(), buildTopologyRepository(DST_SWITCH));
        target.checkFlowForIslConflicts(flow);

        // then a FlowValidationException is thrown
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfSourceVlanIsZeroAndPortIsIslPort() throws FlowValidationException {
        // given
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setSourcePort(ISL_PORT);
        flow.setSourceVlan(0);
        flow.setDestinationSwitch(DST_SWITCH);
        flow.setDestinationPort(DST_PORT);
        flow.setDestinationVlan(DST_VLAN);

        // when
        FlowValidator target = new FlowValidator(new FlowCache(), buildTopologyRepository(SRC_SWITCH));
        target.checkFlowForIslConflicts(flow);

        // then a FlowValidationException is thrown
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfVlanIsZeroAndPortIsIslPort() throws FlowValidationException {
        // given
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setSourcePort(SRC_PORT);
        flow.setSourceVlan(SRC_VLAN);
        flow.setDestinationSwitch(DST_SWITCH);
        flow.setDestinationPort(ISL_PORT);
        flow.setDestinationVlan(0);

        // when
        FlowValidator target = new FlowValidator(new FlowCache(), buildTopologyRepository(DST_SWITCH));
        target.checkFlowForIslConflicts(flow);

        // then a FlowValidationException is thrown
    }

    private TopologyRepository buildTopologyRepository(String switchId) {
        return new PathComputerMock() {
            @Override
            public boolean isIslPort(String swId, int port) {
                return swId.equals(switchId) && ISL_PORT == port;
            }
        };
    }
}
