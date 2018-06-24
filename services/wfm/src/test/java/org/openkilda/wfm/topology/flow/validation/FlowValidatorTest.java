package org.openkilda.wfm.topology.flow.validation;

import org.junit.BeforeClass;
import org.junit.Test;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.pce.cache.FlowCache;

public class FlowValidatorTest {
    private FlowValidator target = new FlowValidator(flowCache);

    private static final FlowCache flowCache = new FlowCache();
    private static final String SRC_SWITCH = "00:00:00:00:00:00:00:01";
    private static final int SRC_PORT = 1;
    private static final int SRC_VLAN = 1;
    private static final String DST_SWITCH = "00:00:00:00:00:00:00:02";
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
        flowCache.pushFlow(new ImmutablePair<>(flow, flow));
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
