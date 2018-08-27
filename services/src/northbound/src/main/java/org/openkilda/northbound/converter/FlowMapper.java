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

package org.openkilda.northbound.converter;

import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.flow.FlowVerificationErrorCode;
import org.openkilda.messaging.info.flow.FlowVerificationResponse;
import org.openkilda.messaging.model.BidirectionalFlow;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowReroutePayload;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.northbound.dto.flows.VerificationOutput;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring")
public interface FlowMapper {

    VerificationOutput toVerificationOutput(FlowVerificationResponse response);

    @Mappings({
            @Mapping(source = "flowId", target = "id"),
            @Mapping(source = "path", target = "path"),
            @Mapping(source = "rerouted", target = "rerouted")
    })
    FlowReroutePayload toReroutePayload(String flowId, PathInfoData path, boolean rerouted);

    @Mappings({
            @Mapping(source = "flowId", target = "id"),
            @Mapping(source = "state", target = "status")
    })
    FlowIdStatusPayload toFlowIdStatusPayload(BidirectionalFlow flow);

    @Mappings({
            @Mapping(source = "flowId", target = "id"),
            @Mapping(source = "forward", target = "forwardPath"),
            @Mapping(source = "reverse", target = "reversePath")
    })
    FlowPathPayload toFlowPathPayload(BidirectionalFlow flow);

    /**
     * Converts {@link Flow} to {@link FlowPayload}.
     */
    default FlowPayload toFlowPayload(Flow flow) {
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
     * Makes flow path as list of {@link PathNodePayload} representation by a {@link Flow} instance.
     * Includes input and output nodes.
     *
     * @param flow the {@link Flow} instance.
     * @return flow path as list of {@link PathNodePayload} representation.
     */
    default List<PathNodePayload> toPathNodePayloadList(Flow flow) {
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
     * Translate Java's error code(enum) into human readable string.
     */
    default String getVerificationError(FlowVerificationErrorCode error) {
        if (error == null) {
            return null;
        }

        String message;
        switch (error) {
            case TIMEOUT:
                message = "No ping for reasonable time";
                break;
            case WRITE_FAILURE:
                message = "Can't send ping";
                break;
            case NOT_CAPABLE:
                message = "Unable to perform flow verification due to unsupported switch (at least one)";
                break;
            default:
                message = error.toString();
        }

        return message;
    }
}
