package org.openkilda.integration.converter;

import java.util.ArrayList;
import java.util.List;

import org.openkilda.integration.model.Flow;
import org.openkilda.integration.model.FlowEndpoint;
import org.openkilda.model.FlowInfo;
import org.openkilda.utility.CollectionUtil;

public final class FlowConverter {

    private FlowConverter() {
    }

    public static List<FlowInfo> toFlowsInfo(final List<Flow> flows) {
        if(!CollectionUtil.isEmpty(flows)) {
            final List<FlowInfo> flowsInfo = new ArrayList<>();
            flows.forEach(flow -> {
                flowsInfo.add(toFlowInfo(flow));
            });
            return flowsInfo;
        }
        return null;
    }

    public static FlowInfo toFlowInfo(final Flow flow) {
        FlowInfo flowInfo = new FlowInfo();
        flowInfo.setFlowid(flow.getId());
        flowInfo.setMaximumBandwidth(flow.getMaximumBandwidth());
        flowInfo.setDescription(flow.getDescription());
        FlowEndpoint source = flow.getSource();
        if (source != null) {
            flowInfo.setSourceSwitch(source.getSwitchId());
            flowInfo.setSrcPort(source.getPortId());
            flowInfo.setSrcVlan(source.getVlanId());
        }
        FlowEndpoint destination = flow.getDestination();
        if (destination != null) {
            flowInfo.setTargetSwitch(destination.getSwitchId());
            flowInfo.setDstPort(destination.getPortId());
            flowInfo.setDstVlan(destination.getVlanId());
        }
        return flowInfo;
    }
}
