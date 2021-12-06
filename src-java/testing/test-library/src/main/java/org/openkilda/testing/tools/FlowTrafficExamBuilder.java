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

package org.openkilda.testing.tools;

import static java.lang.String.format;

import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2;
import org.openkilda.northbound.dto.v2.yflows.SubFlow;
import org.openkilda.northbound.dto.v2.yflows.YFlow;
import org.openkilda.northbound.dto.v2.yflows.YFlowSharedEndpoint;
import org.openkilda.testing.model.topology.TopologyDefinition;
import org.openkilda.testing.model.topology.TopologyDefinition.Switch;
import org.openkilda.testing.model.topology.TopologyDefinition.TraffGen;
import org.openkilda.testing.service.traffexam.FlowNotApplicableException;
import org.openkilda.testing.service.traffexam.TraffExamService;
import org.openkilda.testing.service.traffexam.model.Bandwidth;
import org.openkilda.testing.service.traffexam.model.Exam;
import org.openkilda.testing.service.traffexam.model.FlowBidirectionalExam;
import org.openkilda.testing.service.traffexam.model.Host;
import org.openkilda.testing.service.traffexam.model.TimeLimit;
import org.openkilda.testing.service.traffexam.model.Vlan;
import org.openkilda.testing.service.traffexam.model.YFlowBidirectionalExam;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FlowTrafficExamBuilder {

    private final TraffExamService traffExam;
    private final TopologyDefinition topology;

    private Map<NetworkEndpoint, TraffGen> endpointToTraffGen = new HashMap<>();

    public FlowTrafficExamBuilder(TopologyDefinition topology, TraffExamService traffExam) {
        this.traffExam = traffExam;
        this.topology = topology;

        for (TraffGen traffGen : topology.getActiveTraffGens()) {
            NetworkEndpoint endpoint = new NetworkEndpoint(
                    traffGen.getSwitchConnected().getDpId(), traffGen.getSwitchPort());
            endpointToTraffGen.put(endpoint, traffGen);
        }
    }

    /**
     * Builds bidirectional exam.
     */
    public FlowBidirectionalExam buildBidirectionalExam(FlowPayload flow, long bandwidth, Long duration)
            throws FlowNotApplicableException {
        Optional<TraffGen> source = Optional.ofNullable(
                endpointToTraffGen.get(makeComparableEndpoint(flow.getSource())));
        Optional<TraffGen> dest = Optional.ofNullable(
                endpointToTraffGen.get(makeComparableEndpoint(flow.getDestination())));

        checkIsFlowApplicable(flow.getId(), source.isPresent(), dest.isPresent());

        List<Vlan> srcVlanIds = new ArrayList<Vlan>();
        srcVlanIds.add(new Vlan(flow.getSource().getVlanId()));
        srcVlanIds.add(new Vlan(flow.getSource().getInnerVlanId()));
        List<Vlan> dstVlanIds = new ArrayList<Vlan>();
        dstVlanIds.add(new Vlan(flow.getDestination().getVlanId()));
        dstVlanIds.add(new Vlan(flow.getDestination().getInnerVlanId()));

        //noinspection ConstantConditions
        Host sourceHost = traffExam.hostByName(source.get().getName());
        //noinspection ConstantConditions
        Host destHost = traffExam.hostByName(dest.get().getName());

        Exam forward = Exam.builder()
                .flow(flow)
                .source(sourceHost)
                .sourceVlans(srcVlanIds)
                .dest(destHost)
                .destVlans(dstVlanIds)
                .bandwidthLimit(new Bandwidth(bandwidth))
                .burstPkt(0)
                .timeLimitSeconds(duration != null ? new TimeLimit(duration) : null)
                .build();
        Exam reverse = Exam.builder()
                .flow(flow)
                .source(destHost)
                .sourceVlans(dstVlanIds)
                .dest(sourceHost)
                .destVlans(srcVlanIds)
                .bandwidthLimit(new Bandwidth(bandwidth))
                .burstPkt(0)
                .timeLimitSeconds(duration != null ? new TimeLimit(duration) : null)
                .build();

        return new FlowBidirectionalExam(forward, reverse);
    }

    public FlowBidirectionalExam buildBidirectionalExam(FlowPayload flow) throws FlowNotApplicableException {
        return buildBidirectionalExam(flow, 100, null);
    }


    public FlowBidirectionalExam buildBidirectionalExam(FlowPayload flow, int bandwidth)
            throws FlowNotApplicableException {
        return buildBidirectionalExam(flow, bandwidth, null);
    }

    /**
     * Build Exam in one direction.
     */
    public Exam buildExam(FlowPayload flow, int bandwidth, Long duration) throws FlowNotApplicableException {
        Optional<TraffGen> source = Optional.ofNullable(
                endpointToTraffGen.get(makeComparableEndpoint(flow.getSource())));
        Optional<TraffGen> dest = Optional.ofNullable(
                endpointToTraffGen.get(makeComparableEndpoint(flow.getDestination())));

        //do not allow traffic exam on OF version <1.3. We are not installing meters on OF 1.2 intentionally
        String srcOfVersion = topology.getSwitches().stream().filter(sw ->
                sw.getDpId().equals(flow.getSource().getDatapath())).findFirst()
                .map(Switch::getOfVersion)
                .orElseThrow(() -> new IllegalStateException(
                        format("Switch %s not found", flow.getSource().getDatapath())));
        String dstOfVersion = topology.getSwitches().stream().filter(sw ->
                sw.getDpId().equals(flow.getDestination().getDatapath())).findFirst()
                .map(Switch::getOfVersion)
                .orElseThrow(() -> new IllegalStateException(
                        format("Switch %s not found", flow.getDestination().getDatapath())));

        checkIsFlowApplicable(flow.getId(), source.isPresent() && !"OF_12".equals(srcOfVersion),
                dest.isPresent() && !"OF_12".equals(dstOfVersion));

        List<Vlan> srcVlanIds = new ArrayList<Vlan>();
        srcVlanIds.add(new Vlan(flow.getSource().getVlanId()));
        srcVlanIds.add(new Vlan(flow.getSource().getInnerVlanId()));
        List<Vlan> dstVlanIds = new ArrayList<Vlan>();
        dstVlanIds.add(new Vlan(flow.getDestination().getVlanId()));
        dstVlanIds.add(new Vlan(flow.getDestination().getInnerVlanId()));

        //noinspection ConstantConditions
        Host sourceHost = traffExam.hostByName(source.get().getName());
        //noinspection ConstantConditions
        Host destHost = traffExam.hostByName(dest.get().getName());

        // burst value is hardcoded into floddlight-modules as 1000 kbit/sec, so to overcome this burst we need at least
        // 1024 * 1024 / 8 / 1500 = 87.3...
        return Exam.builder()
                .flow(flow)
                .source(sourceHost)
                .sourceVlans(srcVlanIds)
                .dest(destHost)
                .destVlans(dstVlanIds)
                .bandwidthLimit(new Bandwidth(bandwidth))
                .burstPkt(100)
                .timeLimitSeconds(duration != null ? new TimeLimit(duration) : null)
                .build();
    }

    public Exam buildExam(FlowPayload flow, int bandwidth)
            throws FlowNotApplicableException {
        return buildExam(flow, bandwidth, null);
    }

    public Exam buildExam(FlowPayload flow)
            throws FlowNotApplicableException {
        return buildExam(flow, 100, null);
    }

    /**
     * Build traff exam object for both 'y-flow' subflows in both directions.
     */
    public YFlowBidirectionalExam buildYFlowExam(YFlow flow, long bandwidth, Long duration)
            throws FlowNotApplicableException {
        SubFlow subFlow1 = flow.getSubFlows().get(0);
        SubFlow subFlow2 = flow.getSubFlows().get(1);
        Optional<TraffGen> source = Optional.ofNullable(
                endpointToTraffGen.get(makeComparableEndpoint(flow.getSharedEndpoint())));
        Optional<TraffGen> dest1 = Optional.ofNullable(
                endpointToTraffGen.get(makeComparableEndpoint(subFlow1.getEndpoint())));
        Optional<TraffGen> dest2 = Optional.ofNullable(
                endpointToTraffGen.get(makeComparableEndpoint(subFlow2.getEndpoint())));

        checkIsFlowApplicable(flow.getYFlowId(), source.isPresent(), dest1.isPresent() && dest2.isPresent());

        List<Vlan> srcVlanIds1 = ImmutableList.of(new Vlan(subFlow1.getSharedEndpoint().getVlanId()),
                new Vlan(subFlow1.getSharedEndpoint().getInnerVlanId()));
        List<Vlan> srcVlanIds2 = ImmutableList.of(new Vlan(subFlow2.getSharedEndpoint().getVlanId()),
                new Vlan(subFlow2.getSharedEndpoint().getInnerVlanId()));
        List<Vlan> dstVlanIds1 = ImmutableList.of(new Vlan(subFlow1.getEndpoint().getVlanId()),
                new Vlan(subFlow1.getEndpoint().getInnerVlanId()));
        List<Vlan> dstVlanIds2 = ImmutableList.of(new Vlan(subFlow2.getEndpoint().getVlanId()),
                new Vlan(subFlow2.getEndpoint().getInnerVlanId()));

        //noinspection ConstantConditions
        Host sourceHost = traffExam.hostByName(source.get().getName());
        //noinspection ConstantConditions
        Host destHost1 = traffExam.hostByName(dest1.get().getName());
        Host destHost2 = traffExam.hostByName(dest2.get().getName());

        Exam forward1 = Exam.builder()
                .flow(null)
                .source(sourceHost)
                .sourceVlans(srcVlanIds1)
                .dest(destHost1)
                .destVlans(dstVlanIds1)
                .bandwidthLimit(new Bandwidth(bandwidth))
                .burstPkt(200)
                .timeLimitSeconds(duration != null ? new TimeLimit(duration) : null)
                .build();
        Exam forward2 = Exam.builder()
                .flow(null)
                .source(sourceHost)
                .sourceVlans(srcVlanIds2)
                .dest(destHost2)
                .destVlans(dstVlanIds2)
                .bandwidthLimit(new Bandwidth(bandwidth))
                .burstPkt(200)
                .timeLimitSeconds(duration != null ? new TimeLimit(duration) : null)
                .build();
        Exam reverse1 = Exam.builder()
                .flow(null)
                .source(destHost1)
                .sourceVlans(dstVlanIds1)
                .dest(sourceHost)
                .destVlans(srcVlanIds1)
                .bandwidthLimit(new Bandwidth(bandwidth))
                .burstPkt(200)
                .timeLimitSeconds(duration != null ? new TimeLimit(duration) : null)
                .build();
        Exam reverse2 = Exam.builder()
                .flow(null)
                .source(destHost2)
                .sourceVlans(dstVlanIds2)
                .dest(sourceHost)
                .destVlans(srcVlanIds2)
                .bandwidthLimit(new Bandwidth(bandwidth))
                .burstPkt(200)
                .timeLimitSeconds(duration != null ? new TimeLimit(duration) : null)
                .build();

        return new YFlowBidirectionalExam(forward1, reverse1, forward2, reverse2);
    }

    private void checkIsFlowApplicable(String flowId, boolean sourceApplicable, boolean destApplicable)
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
            throw new FlowNotApplicableException(format(
                    "Flow's %s %s not applicable for traffic examination.", flowId, message));
        }
    }

    private NetworkEndpoint makeComparableEndpoint(FlowEndpointPayload flowEndpoint) {
        return new NetworkEndpoint(flowEndpoint);
    }

    private NetworkEndpoint makeComparableEndpoint(FlowEndpointV2 flowEndpoint) {
        return new NetworkEndpoint(flowEndpoint.getSwitchId(), flowEndpoint.getPortNumber());
    }

    private NetworkEndpoint makeComparableEndpoint(YFlowSharedEndpoint flowEndpoint) {
        return new NetworkEndpoint(flowEndpoint.getSwitchId(), flowEndpoint.getPortNumber());
    }
}
