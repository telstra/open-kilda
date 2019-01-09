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

package org.openkilda.wfm.topology.flow.service;

import static java.lang.String.format;

import org.openkilda.messaging.command.flow.BaseInstallFlow;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.openkilda.messaging.command.flow.InstallTransitFlow;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowSegment;
import org.openkilda.model.OutputVlanType;
import org.openkilda.model.SwitchId;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class FlowCommandFactory {
    // The default timeBasedGenerator() utilizes SecureRandom for the location part and time+sequence for the time part.
    private final NoArgGenerator transactionIdGenerator = Generators.timeBasedGenerator();

    /**
     * Generates install transit and egress rules commands for a flow.
     *
     * @param flow     flow to be installed.
     * @param segments flow segments to be used for building of install rules.
     * @return list of commands
     */
    public List<InstallTransitFlow> createInstallTransitAndEgressRulesForFlow(Flow flow, List<FlowSegment> segments) {
        if (flow.isOneSwitchFlow()) {
            return Collections.emptyList();
        }
        requireSegments(segments);

        List<FlowSegment> orderedSegments = segments.stream()
                .sorted(Comparator.comparingInt(FlowSegment::getSeqId))
                .collect(Collectors.toList());

        List<InstallTransitFlow> commands = new ArrayList<>();

        for (int i = 1; i < orderedSegments.size(); i++) {
            FlowSegment src = orderedSegments.get(i - 1);
            FlowSegment dst = orderedSegments.get(i);

            commands.add(buildInstallTransitFlow(flow, src.getDestSwitch().getSwitchId(), src.getDestPort(),
                    dst.getSrcPort(), src.getCookie()));
        }

        FlowSegment egressSegment = orderedSegments.get(orderedSegments.size() - 1);
        if (!egressSegment.getDestSwitch().getSwitchId().equals(flow.getDestSwitch().getSwitchId())) {
            throw new IllegalStateException(
                    format("FlowSegment was not found for egress flow rule, flowId: %s", flow.getFlowId()));
        }
        OutputVlanType outputVlanType = getOutputVlanType(flow);
        commands.add(buildInstallEgressFlow(flow, egressSegment.getDestPort(), egressSegment.getCookie(),
                outputVlanType));
        return commands;
    }

    /**
     * Generates install ingress / one switch rules commands for a flow.
     *
     * @param flow     flow to be installed.
     * @param segments flow segments to be used for building of install rules.
     * @return list of commands
     */
    public BaseInstallFlow createInstallIngressRulesForFlow(Flow flow, List<FlowSegment> segments) {
        OutputVlanType outputVlanType = getOutputVlanType(flow);
        if (flow.isOneSwitchFlow()) {
            return makeOneSwitchRule(flow, outputVlanType);
        }
        requireSegments(segments);

        FlowSegment ingressSegment = segments.stream()
                .filter(segment -> segment.getSrcSwitch().getSwitchId().equals(flow.getSrcSwitch().getSwitchId()))
                .findAny()
                .orElseThrow(() -> new IllegalStateException(
                        format("FlowSegment was not found for ingress flow rule, flowId: %s", flow.getFlowId())));
        return buildInstallIngressFlow(flow, ingressSegment.getSrcPort(), ingressSegment.getCookie(),
                outputVlanType);
    }

    /**
     * Generates remove transit and egress rules commands for a flow.
     *
     * @param flow     flow to be deleted.
     * @param segments flow segments to be used for building of install rules.
     * @return list of commands
     */
    public List<RemoveFlow> createRemoveTransitAndEgressRulesForFlow(Flow flow, List<FlowSegment> segments) {
        if (flow.isOneSwitchFlow()) {
            // Removing of single switch rules is done with no output port in criteria.
            return Collections.emptyList();
        }
        requireSegments(segments);

        List<FlowSegment> orderedSegments = segments.stream()
                .sorted(Comparator.comparingInt(FlowSegment::getSeqId))
                .collect(Collectors.toList());

        List<RemoveFlow> commands = new ArrayList<>();

        for (int i = 1; i < orderedSegments.size(); i++) {
            FlowSegment src = orderedSegments.get(i - 1);
            FlowSegment dst = orderedSegments.get(i);

            commands.add(buildRemoveTransitFlow(flow, src.getDestSwitch().getSwitchId(), src.getDestPort(),
                    dst.getSrcPort(), src.getCookie()));
        }

        FlowSegment egressSegment = orderedSegments.get(orderedSegments.size() - 1);
        if (!egressSegment.getDestSwitch().getSwitchId().equals(flow.getDestSwitch().getSwitchId())) {
            throw new IllegalStateException(
                    format("FlowSegment was not found for egress flow rule, flowId: %s", flow.getFlowId()));
        }
        commands.add(buildRemoveEgressFlow(flow, egressSegment.getDestPort(), egressSegment.getCookie()));
        return commands;
    }

    /**
     * Generates remove ingress rules commands for a flow.
     *
     * @param flow     flow to be deleted.
     * @param segments flow segments to be used for building of install rules.
     * @return list of commands
     */
    public RemoveFlow createRemoveIngressRulesForFlow(Flow flow, List<FlowSegment> segments) {
        if (flow.isOneSwitchFlow()) {
            // Removing of single switch rules is done with no output port in criteria.
            return buildRemoveIngressFlow(flow, null, flow.getCookie());
        }
        requireSegments(segments);

        FlowSegment ingressSegment = segments.stream()
                .filter(segment -> segment.getSrcSwitch().getSwitchId().equals(flow.getSrcSwitch().getSwitchId()))
                .findAny()
                .orElseThrow(() -> new IllegalStateException(
                        format("FlowSegment was not found for ingress flow rule, flowId: %s", flow.getFlowId())));
        return buildRemoveIngressFlow(flow, ingressSegment.getSrcPort(), ingressSegment.getCookie());
    }

    private void requireSegments(List<FlowSegment> segments) {
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Neither one switch flow nor flow segments provided");
        }
    }

    private InstallEgressFlow buildInstallEgressFlow(Flow flow, int inputPortNo, long segmentCookie,
                                                     OutputVlanType outputVlanType) {
        if (segmentCookie == 0) {
            segmentCookie = flow.getCookie();
        }
        return new InstallEgressFlow(transactionIdGenerator.generate(), flow.getFlowId(),
                segmentCookie, flow.getDestSwitch().getSwitchId(), inputPortNo, flow.getDestPort(),
                flow.getTransitVlan(), flow.getDestVlan(), outputVlanType);
    }

    private RemoveFlow buildRemoveEgressFlow(Flow flow, int inputPortNo, long segmentCookie) {
        if (segmentCookie == 0) {
            segmentCookie = flow.getCookie();
        }
        DeleteRulesCriteria criteria = new DeleteRulesCriteria(segmentCookie, inputPortNo, flow.getTransitVlan(),
                0, flow.getDestPort());
        return new RemoveFlow(transactionIdGenerator.generate(), flow.getFlowId(), segmentCookie,
                flow.getDestSwitch().getSwitchId(), null, criteria);
    }

    private InstallTransitFlow buildInstallTransitFlow(Flow flow, SwitchId switchId, int inputPortNo, int outputPortNo,
                                                       long segmentCookie) {
        if (segmentCookie == 0) {
            segmentCookie = flow.getCookie();
        }
        return new InstallTransitFlow(transactionIdGenerator.generate(), flow.getFlowId(), segmentCookie,
                switchId, inputPortNo, outputPortNo, flow.getTransitVlan());
    }

    private RemoveFlow buildRemoveTransitFlow(Flow flow, SwitchId switchId, int inputPortNo, int outputPortNo,
                                              long segmentCookie) {
        if (segmentCookie == 0) {
            segmentCookie = flow.getCookie();
        }
        DeleteRulesCriteria criteria = new DeleteRulesCriteria(segmentCookie, inputPortNo, flow.getTransitVlan(),
                0, outputPortNo);
        return new RemoveFlow(transactionIdGenerator.generate(), flow.getFlowId(), segmentCookie,
                switchId, null, criteria);
    }

    private BaseInstallFlow buildInstallIngressFlow(Flow flow, int outputPortNo, long segmentCookie,
                                                    OutputVlanType outputVlanType) {
        if (segmentCookie == 0) {
            segmentCookie = flow.getCookie();
        }
        return new InstallIngressFlow(transactionIdGenerator.generate(), flow.getFlowId(),
                segmentCookie, flow.getSrcSwitch().getSwitchId(), flow.getSrcPort(),
                outputPortNo, flow.getSrcVlan(), flow.getTransitVlan(), outputVlanType,
                flow.getBandwidth(), flow.getMeterLongValue());
    }

    private RemoveFlow buildRemoveIngressFlow(Flow flow, Integer outputPortNo, long segmentCookie) {
        if (segmentCookie == 0) {
            segmentCookie = flow.getCookie();
        }
        DeleteRulesCriteria ingressCriteria = new DeleteRulesCriteria(segmentCookie, flow.getSrcPort(),
                flow.getSrcVlan(), 0, outputPortNo);
        return new RemoveFlow(transactionIdGenerator.generate(), flow.getFlowId(),
                segmentCookie, flow.getSrcSwitch().getSwitchId(), flow.getMeterLongValue(), ingressCriteria);
    }

    private BaseInstallFlow makeOneSwitchRule(Flow flow, OutputVlanType outputVlanType) {
        return new InstallOneSwitchFlow(transactionIdGenerator.generate(),
                flow.getFlowId(), flow.getCookie(), flow.getSrcSwitch().getSwitchId(), flow.getSrcPort(),
                flow.getDestPort(), flow.getSrcVlan(), flow.getDestVlan(),
                outputVlanType, flow.getBandwidth(), flow.getMeterLongValue());
    }

    private OutputVlanType getOutputVlanType(Flow flow) {
        int sourceVlan = flow.getSrcVlan();
        int dstVlan = flow.getDestVlan();
        if (sourceVlan == 0) {
            return dstVlan == 0 ? OutputVlanType.NONE : OutputVlanType.PUSH;
        }
        return dstVlan == 0 ? OutputVlanType.POP : OutputVlanType.REPLACE;
    }
}
