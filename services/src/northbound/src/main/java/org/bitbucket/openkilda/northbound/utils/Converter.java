package org.bitbucket.openkilda.northbound.utils;

import org.bitbucket.openkilda.messaging.info.event.PathInfoData;
import org.bitbucket.openkilda.messaging.model.Flow;
import org.bitbucket.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPathPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPayload;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Northbound utility methods.
 */
public final class Converter {
    /**
     * Builds {@link Flow} instance by {@link FlowPayload} instance.
     *
     * @param flowPayload {@link FlowPayload} instance
     * @return {@link Flow} instance
     */
    public static Flow buildFlowByFlowPayload(FlowPayload flowPayload) {
        return new Flow(
                flowPayload.getId(),
                flowPayload.getMaximumBandwidth(),
                flowPayload.getDescription(),
                flowPayload.getSource().getSwitchId(),
                flowPayload.getSource().getPortId(),
                flowPayload.getSource().getVlanId(),
                flowPayload.getDestination().getSwitchId(),
                flowPayload.getDestination().getPortId(),
                flowPayload.getDestination().getVlanId());
    }

    /**
     * Builds {@link FlowPayload} instance by {@link Flow} instance.
     *
     * @param flow {@link Flow} instance
     * @return {@link FlowPayload} instance
     */
    public static FlowPayload buildFlowPayloadByFlow(Flow flow) {
        return new FlowPayload(
                flow.getFlowId(),
                new FlowEndpointPayload(
                        flow.getSourceSwitch(),
                        flow.getSourcePort(),
                        flow.getSourceVlan()),
                new FlowEndpointPayload(
                        flow.getDestinationSwitch(),
                        flow.getDestinationPort(),
                        flow.getDestinationVlan()),
                flow.getBandwidth(),
                flow.getDescription(),
                flow.getLastUpdated());
    }

    /**
     * Builds list of {@link FlowPayload} instances by list of {@link Flow} instance.
     *
     * @param flows list of {@link Flow} instance
     * @return list of {@link FlowPayload} instance
     */
    public static List<FlowPayload> buildFlowsPayloadByFlows(List<Flow> flows) {
        return flows.stream().map(Converter::buildFlowPayloadByFlow).collect(Collectors.toList());
    }

    /**
     * Builds {@link FlowPayload} instance by {@link Flow} instance.
     *
     * @param flowId flow id
     * @param path {@link PathInfoData} instance
     * @return {@link FlowPayload} instance
     */
    public static FlowPathPayload buildFlowPathPayloadByFlowPath(String flowId, PathInfoData path) {
        return new FlowPathPayload(flowId, path);
    }
}
