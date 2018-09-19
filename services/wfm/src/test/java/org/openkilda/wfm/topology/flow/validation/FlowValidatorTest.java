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
import org.openkilda.messaging.model.FlowPair;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.pce.cache.FlowCache;

import org.junit.BeforeClass;
import org.junit.Test;

public class FlowValidatorTest {
    private FlowValidator target = new FlowValidator(flowCache);

    private static final FlowCache flowCache = new FlowCache();
    private static final SwitchId SRC_SWITCH = new SwitchId("00:00:00:00:00:00:00:01");
    private static final int SRC_PORT = 1;
    private static final int SRC_VLAN = 1;
    private static final SwitchId DST_SWITCH = new SwitchId("00:00:00:00:00:00:00:02");
    private static final int DST_PORT = 5;
    private static final int DST_VLAN = 5;

    @BeforeClass
    public static void initCache() {
        Flow flow = new Flow();
        flow.setFlowId("1");
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setSourcePort(SRC_PORT);
        flow.setSourceVlan(SRC_VLAN);

        flow.setDestinationSwitch(DST_SWITCH);
        flow.setDestinationPort(DST_PORT);
        flow.setDestinationVlan(DST_VLAN);
        flowCache.pushFlow(new FlowPair<>(flow, flow));
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfSourceVlanIsZeroAndPortIsOccupied() throws FlowValidationException {
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setSourcePort(SRC_PORT);
        flow.setSourceVlan(0);

        target.checkFlowForEndpointConflicts(flow);
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfSourceVlanIsAlreadyOccupied() throws FlowValidationException {
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setSourcePort(SRC_PORT);
        flow.setSourceVlan(SRC_VLAN);

        target.checkFlowForEndpointConflicts(flow);
    }

    @Test
    public void shouldNotFailIfSourceVlanIsNotOccupied() throws FlowValidationException {
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setSourcePort(SRC_PORT);
        flow.setSourceVlan(SRC_VLAN + 1);

        target.checkFlowForEndpointConflicts(flow);
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfDestinationVlanIsZeroAndPortIsOccupied() throws FlowValidationException {
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setSourcePort(SRC_PORT);
        flow.setSourceVlan(SRC_VLAN + 1);
        flow.setDestinationSwitch(DST_SWITCH);
        flow.setDestinationPort(DST_PORT);
        flow.setDestinationVlan(0);

        target.checkFlowForEndpointConflicts(flow);
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailIfDestinationVlanIsAlreadyOccupied() throws FlowValidationException {
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setSourcePort(SRC_PORT);
        flow.setSourceVlan(SRC_VLAN + 1);
        flow.setDestinationSwitch(DST_SWITCH);
        flow.setDestinationPort(DST_PORT);
        flow.setDestinationVlan(DST_VLAN);

        target.checkFlowForEndpointConflicts(flow);
    }

    @Test
    public void shouldNotFailIfDestinationVlanIsNotOccupied() throws FlowValidationException {
        Flow flow = new Flow();
        flow.setSourceSwitch(SRC_SWITCH);
        flow.setSourcePort(SRC_PORT);
        flow.setSourceVlan(SRC_VLAN + 1);
        flow.setDestinationSwitch(DST_SWITCH);
        flow.setDestinationPort(DST_PORT);
        flow.setDestinationVlan(DST_VLAN + 1);

        target.checkFlowForEndpointConflicts(flow);
    }

    @Test(expected = FlowValidationException.class)
    public void shouldFailForNegativeBandwidth() throws FlowValidationException {
        Flow flow = new Flow();
        flow.setBandwidth(-1);

        target.checkBandwidth(flow);
    }
}
