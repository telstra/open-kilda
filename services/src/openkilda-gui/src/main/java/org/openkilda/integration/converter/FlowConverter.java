package org.openkilda.integration.converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.openkilda.integration.model.Flow;
import org.openkilda.integration.model.FlowEndpoint;
import org.openkilda.integration.service.SwitchIntegrationService;
import org.openkilda.model.FlowInfo;
import org.openkilda.utility.CollectionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FlowConverter {
    @Autowired
    SwitchIntegrationService switchIntegrationService;

    public List<FlowInfo> toFlowsInfo(final List<Flow> flows) {
        if (!CollectionUtil.isEmpty(flows)) {
            final List<FlowInfo> flowsInfo = new ArrayList<>();
            final Map<String, String> csNames = switchIntegrationService.getCustomSwitchNameFromFile();
            flows.forEach(flow -> {
                flowsInfo.add(toFlowInfo(flow,csNames));
            });
            return flowsInfo;
        }
        return null;
    }

    public FlowInfo toFlowInfo(final Flow flow,Map <String,String> csNames) {
        FlowInfo flowInfo = new FlowInfo();
        flowInfo.setFlowid(flow.getId());
        flowInfo.setMaximumBandwidth(flow.getMaximumBandwidth());
        flowInfo.setDescription(flow.getDescription());
        flowInfo.setStatus(flow.getStatus().toUpperCase());
        FlowEndpoint source = flow.getSource();
        if (source != null) {
            String switchName = switchIntegrationService.customSwitchName(csNames,
                    source.getSwitchId());
            flowInfo.setSourceSwitchName(switchName);
            flowInfo.setSourceSwitch(source.getSwitchId());
            flowInfo.setSrcPort(source.getPortId());
            flowInfo.setSrcVlan(source.getVlanId());
        }
        FlowEndpoint destination = flow.getDestination();
        if (destination != null) {
            String switchName = switchIntegrationService.customSwitchName(csNames,
                    destination.getSwitchId());
            flowInfo.setTargetSwitchName(switchName);
            flowInfo.setTargetSwitch(destination.getSwitchId());
            flowInfo.setDstPort(destination.getPortId());
            flowInfo.setDstVlan(destination.getVlanId());
        }
        return flowInfo;
    }
    
    public Flow toFlowWithSwitchNames(final Flow flow) {
        final Map<String, String> csNames = switchIntegrationService.getCustomSwitchNameFromFile();
        FlowEndpoint source = flow.getSource();
        if (source != null) {
            String switchName =
                    switchIntegrationService.customSwitchName(csNames, source.getSwitchId());
            source.setSwitchName(switchName);
        }
        FlowEndpoint destination = flow.getDestination();
        if (destination != null) {
            String switchName =
                    switchIntegrationService.customSwitchName(csNames, destination.getSwitchId());
            destination.setSwitchName(switchName);
        }
        return flow;
    }
}
