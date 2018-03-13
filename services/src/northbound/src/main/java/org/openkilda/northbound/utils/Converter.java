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

package org.openkilda.northbound.utils;

import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;

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
                flowPayload.isIgnoreBandwidth(),
                flowPayload.getDescription(),
                flowPayload.getSource().getSwitchDpId(),
                flowPayload.getSource().getPortId(),
                flowPayload.getSource().getVlanId(),
                flowPayload.getDestination().getSwitchDpId(),
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
                flow.isIgnoreBandwidth(),
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
