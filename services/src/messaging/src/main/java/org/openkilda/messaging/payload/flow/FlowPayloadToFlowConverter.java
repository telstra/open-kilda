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

package org.openkilda.messaging.payload.flow;

import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.model.BidirectionalFlow;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;

import java.util.ArrayList;
import java.util.List;

/**
 * Northbound utility methods.
 */
public final class FlowPayloadToFlowConverter {
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
                flow.getLastUpdated(),
                flow.getState().getState());
    }

    /**
     * Builds {@link FlowPayload} instance by {@link ImmutablePair} instance.
     *
     * @param flow {@link BidirectionalFlow} the bidirectional flow with paths
     * @return {@link FlowPayload} instance
     */
    public static FlowPathPayload buildFlowPathPayload(BidirectionalFlow flow) {
        return new FlowPathPayload(
                flow.getFlowId(),
                convertFlowToPathNodePayloadList(flow.getForward()),
                convertFlowToPathNodePayloadList(flow.getReverse())
        );
    }

    /**
     * Makes flow path as list of {@link PathNodePayload} representation by a {@link Flow} instance.
     * Includes input and output nodes.
     *
     * @param flow the {@link Flow} instance.
     * @return flow path as list of {@link PathNodePayload} representation.
     */
    private static List<PathNodePayload> convertFlowToPathNodePayloadList(Flow flow) {
        List<PathNode> path = new ArrayList<>(flow.getFlowPath().getPath());
        // add input and output nodes
        path.add(0, new PathNode(flow.getSourceSwitch(), flow.getSourcePort(), 0));
        path.add(new PathNode(flow.getDestinationSwitch(), flow.getDestinationPort(), 0));

        List<PathNodePayload> resultList = new ArrayList<>();
        for (int i = 1; i < path.size(); i += 2) {
            PathNode inputNode = path.get(i - 1);
            PathNode outputNode = path.get(i);

            resultList.add(
                    new PathNodePayload(inputNode.getSwitchId(), inputNode.getPortNo(), outputNode.getPortNo()));
        }
        return resultList;
    }

    /**
     * Builds {@link FlowReroutePayload} instance by {@link Flow} instance.
     *
     * @param flowId flow id
     * @param path {@link PathInfoData} instance
     * @return {@link FlowReroutePayload} instance
     */
    public static FlowReroutePayload buildReroutePayload(String flowId, PathInfoData path, boolean rerouted) {
        return new FlowReroutePayload(flowId, path, rerouted);
    }

    private FlowPayloadToFlowConverter() {
    }
}
