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

package org.openkilda.atdd.staging.helpers;

import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.testing.model.topology.TopologyDefinition;
import org.openkilda.testing.model.topology.TopologyDefinition.TraffGen;
import org.openkilda.testing.service.traffexam.FlowNotApplicableException;
import org.openkilda.testing.service.traffexam.TraffExamService;
import org.openkilda.testing.service.traffexam.model.Bandwidth;
import org.openkilda.testing.service.traffexam.model.Exam;
import org.openkilda.testing.service.traffexam.model.FlowBidirectionalExam;
import org.openkilda.testing.service.traffexam.model.Host;
import org.openkilda.testing.service.traffexam.model.Vlan;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class FlowTrafficExamBuilder {

    private final TraffExamService traffExam;

    private Map<NetworkEndpoint, TraffGen> endpointToTraffGen = new HashMap<>();

    public FlowTrafficExamBuilder(TopologyDefinition topology, TraffExamService traffExam) {
        this.traffExam = traffExam;

        for (TraffGen traffGen : topology.getActiveTraffGens()) {
            NetworkEndpoint endpoint = new NetworkEndpoint(
                    traffGen.getSwitchConnected().getDpId(), traffGen.getSwitchPort());
            endpointToTraffGen.put(endpoint, traffGen);
        }
    }

    /**
     * Builds bidirectional exam.
     */
    public FlowBidirectionalExam buildBidirectionalExam(FlowPayload flow, int bandwidth)
            throws FlowNotApplicableException {
        Optional<TraffGen> source = Optional.ofNullable(
                endpointToTraffGen.get(makeComparableEndpoint(flow.getSource())));
        Optional<TraffGen> dest = Optional.ofNullable(
                endpointToTraffGen.get(makeComparableEndpoint(flow.getDestination())));

        checkIsFlowApplicable(flow, source.isPresent(), dest.isPresent());

        //noinspection ConstantConditions
        Host sourceHost = traffExam.hostByName(source.get().getName());
        //noinspection ConstantConditions
        Host destHost = traffExam.hostByName(dest.get().getName());

        // burst value is hardcoded into floddlight-modules as 1000 kbit/sec, so to overcome this burst we need at least
        // 1024 * 1024 / 8 / 1500 = 87.3...
        Exam forward = Exam.builder()
                .flow(flow)
                .source(sourceHost)
                .sourceVlan(new Vlan(flow.getSource().getVlanId()))
                .dest(destHost)
                .destVlan(new Vlan(flow.getDestination().getVlanId()))
                .bandwidthLimit(new Bandwidth(bandwidth))
                .burstPkt(100)
                .build();
        Exam reverse = Exam.builder()
                .flow(flow)
                .source(destHost)
                .sourceVlan(new Vlan(flow.getDestination().getVlanId()))
                .dest(sourceHost)
                .destVlan(new Vlan(flow.getSource().getVlanId()))
                .bandwidthLimit(new Bandwidth(bandwidth))
                .burstPkt(100)
                .build();

        return new FlowBidirectionalExam(forward, reverse);
    }

    /**
     * Build Exam in one direction.
     */
    public Exam buildExam(FlowPayload flow, int bandwidth) throws FlowNotApplicableException {
        Optional<TraffGen> source = Optional.ofNullable(
                endpointToTraffGen.get(makeComparableEndpoint(flow.getSource())));
        Optional<TraffGen> dest = Optional.ofNullable(
                endpointToTraffGen.get(makeComparableEndpoint(flow.getDestination())));

        checkIsFlowApplicable(flow, source.isPresent(), dest.isPresent());

        //noinspection ConstantConditions
        Host sourceHost = traffExam.hostByName(source.get().getName());
        //noinspection ConstantConditions
        Host destHost = traffExam.hostByName(dest.get().getName());

        // burst value is hardcoded into floddlight-modules as 1000 kbit/sec, so to overcome this burst we need at least
        // 1024 * 1024 / 8 / 1500 = 87.3...
        return Exam.builder()
                .flow(flow)
                .source(sourceHost)
                .sourceVlan(new Vlan(flow.getSource().getVlanId()))
                .dest(destHost)
                .destVlan(new Vlan(flow.getDestination().getVlanId()))
                .bandwidthLimit(new Bandwidth(bandwidth))
                .burstPkt(100)
                .build();
    }

    private void checkIsFlowApplicable(FlowPayload flow, boolean sourceApplicable, boolean destApplicable)
            throws FlowNotApplicableException {
        String message;

        if (!sourceApplicable && !destApplicable) {
            message = "source endpoint and destination endpoint are";
        } else if (!sourceApplicable) {
            message = "source endpoint is";
        } else if (!destApplicable) {
            message = "dest endpoint is";
        } else {
            message = null;
        }

        if (message != null) {
            throw new FlowNotApplicableException(String.format(
                    "Flow's %s %s not applicable for traffic examination.", flow.getId(), message));
        }
    }

    private NetworkEndpoint makeComparableEndpoint(FlowEndpointPayload flowEndpoint) {
        return new NetworkEndpoint(flowEndpoint);
    }
}
