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
import org.openkilda.model.FlowPath;
import org.openkilda.model.MeterId;
import org.openkilda.model.OutputVlanType;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.model.TransitVlan;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class FlowCommandFactory {
    // The default timeBasedGenerator() utilizes SecureRandom for the location part and time+sequence for the time part.
    private final NoArgGenerator transactionIdGenerator = Generators.timeBasedGenerator();

    /**
     * Generates install transit and egress rules commands for a flow.
     *
     * @param flow     flow to be installed.
     * @param flowPath flow path with segments to be used for building of install rules.
     * @return list of commands
     */
    public List<InstallTransitFlow> createInstallTransitAndEgressRulesForFlow(Flow flow, FlowPath flowPath,
                                                                              TransitVlan transitVlan) {
        if (flow.isOneSwitchFlow()) {
            return Collections.emptyList();
        }
        List<PathSegment> segments = flowPath.getSegments();
        requireSegments(segments);

        List<InstallTransitFlow> commands = new ArrayList<>();

        for (int i = 1; i < segments.size(); i++) {
            PathSegment src = segments.get(i - 1);
            PathSegment dst = segments.get(i);

            commands.add(buildInstallTransitFlow(flowPath, src.getDestSwitch().getSwitchId(), src.getDestPort(),
                    dst.getSrcPort(), transitVlan));
        }

        PathSegment egressSegment = segments.get(segments.size() - 1);
        if (!egressSegment.getDestSwitch().getSwitchId().equals(flowPath.getDestSwitch().getSwitchId())) {
            throw new IllegalStateException(
                    format("FlowSegment was not found for egress flow rule, flowId: %s", flow.getFlowId()));
        }
        commands.add(buildInstallEgressFlow(flow, flowPath, egressSegment.getDestPort(), transitVlan));
        return commands;
    }

    /**
     * Generates install ingress / one switch rules commands for a flow.
     *
     * @param flow     flow to be installed.
     * @param flowPath flow path with segments to be used for building of install rules.
     * @return list of commands
     */
    public BaseInstallFlow createInstallIngressRulesForFlow(Flow flow, FlowPath flowPath, TransitVlan transitVlan) {
        if (flow.isOneSwitchFlow()) {
            return makeOneSwitchRule(flow, flowPath);
        }
        List<PathSegment> segments = flowPath.getSegments();
        requireSegments(segments);

        PathSegment ingressSegment = segments.get(0);
        if (!ingressSegment.getSrcSwitch().getSwitchId().equals(flowPath.getSrcSwitch().getSwitchId())) {
            throw new IllegalStateException(
                    format("FlowSegment was not found for ingress flow rule, flowId: %s", flow.getFlowId()));
        }

        return buildInstallIngressFlow(flow, flowPath, ingressSegment.getSrcPort(), transitVlan);
    }

    /**
     * Generates remove transit and egress rules commands for a flow.
     *
     * @param flow     flow to be deleted.
     * @param flowPath flow path with segments to be used for building of install rules.
     * @return list of commands
     */
    public List<RemoveFlow> createRemoveTransitAndEgressRulesForFlow(Flow flow, FlowPath flowPath,
                                                                     TransitVlan transitVlan) {
        if (flow.isOneSwitchFlow()) {
            // Removing of single switch rules is done with no output port in criteria.
            return Collections.emptyList();
        }
        List<PathSegment> segments = flowPath.getSegments();
        requireSegments(segments);

        List<RemoveFlow> commands = new ArrayList<>();

        for (int i = 1; i < segments.size(); i++) {
            PathSegment src = segments.get(i - 1);
            PathSegment dst = segments.get(i);

            commands.add(buildRemoveTransitFlow(flowPath, src.getDestSwitch().getSwitchId(), src.getDestPort(),
                    dst.getSrcPort(), transitVlan));
        }

        PathSegment egressSegment = segments.get(segments.size() - 1);
        if (!egressSegment.getDestSwitch().getSwitchId().equals(flowPath.getDestSwitch().getSwitchId())) {
            throw new IllegalStateException(
                    format("FlowSegment was not found for egress flow rule, flowId: %s", flow.getFlowId()));
        }
        commands.add(buildRemoveEgressFlow(flow, flowPath, egressSegment.getDestPort(), transitVlan));
        return commands;
    }

    /**
     * Generates remove ingress rules commands for a flow.
     *
     * @param flow     flow to be deleted.
     * @param flowPath flow path with segments to be used for building of install rules.
     * @return list of commands
     */
    public RemoveFlow createRemoveIngressRulesForFlow(Flow flow, FlowPath flowPath) {
        if (flow.isOneSwitchFlow()) {
            // Removing of single switch rules is done with no output port in criteria.
            return buildRemoveIngressFlow(flow, flowPath, null);
        }
        List<PathSegment> segments = flowPath.getSegments();
        requireSegments(segments);

        PathSegment ingressSegment = segments.get(0);
        if (!ingressSegment.getSrcSwitch().getSwitchId().equals(flowPath.getSrcSwitch().getSwitchId())) {
            throw new IllegalStateException(
                    format("FlowSegment was not found for ingress flow rule, flowId: %s", flow.getFlowId()));
        }

        return buildRemoveIngressFlow(flow, flowPath, ingressSegment.getSrcPort());
    }

    private void requireSegments(List<PathSegment> segments) {
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Neither one switch flow nor path segments provided");
        }
    }

    private InstallEgressFlow buildInstallEgressFlow(Flow flow, FlowPath flowPath, int inputPortNo,
                                                     TransitVlan transitVlan) {
        boolean isForward = flow.isForward(flowPath);
        SwitchId switchId = isForward ? flow.getDestSwitch().getSwitchId() : flow.getSrcSwitch().getSwitchId();
        int outPort = isForward ? flow.getDestPort() : flow.getSrcPort();
        int outVlan = isForward ? flow.getDestVlan() : flow.getSrcVlan();

        return new InstallEgressFlow(transactionIdGenerator.generate(), flow.getFlowId(),
                flowPath.getCookie().getValue(), switchId, inputPortNo, outPort,
                transitVlan.getVlan(), outVlan, getOutputVlanType(flow, flowPath));
    }

    private RemoveFlow buildRemoveEgressFlow(Flow flow, FlowPath flowPath, int inputPortNo, TransitVlan transitVlan) {
        boolean isForward = flow.isForward(flowPath);
        SwitchId switchId = isForward ? flow.getDestSwitch().getSwitchId() : flow.getSrcSwitch().getSwitchId();
        int outPort = isForward ? flow.getDestPort() : flow.getSrcPort();

        long cookie = flowPath.getCookie().getValue();
        DeleteRulesCriteria criteria = new DeleteRulesCriteria(cookie, inputPortNo, transitVlan.getVlan(),
                0, outPort);
        return new RemoveFlow(transactionIdGenerator.generate(), flow.getFlowId(), cookie,
                switchId, null, criteria);
    }

    private InstallTransitFlow buildInstallTransitFlow(FlowPath flowPath, SwitchId switchId,
                                                       int inputPortNo, int outputPortNo, TransitVlan transitVlan) {
        return new InstallTransitFlow(transactionIdGenerator.generate(), flowPath.getFlow().getFlowId(),
                flowPath.getCookie().getValue(), switchId, inputPortNo, outputPortNo, transitVlan.getVlan());
    }

    private RemoveFlow buildRemoveTransitFlow(FlowPath flowPath, SwitchId switchId,
                                              int inputPortNo, int outputPortNo, TransitVlan transitVlan) {
        long cookie = flowPath.getCookie().getValue();
        DeleteRulesCriteria criteria = new DeleteRulesCriteria(cookie,
                inputPortNo, transitVlan.getVlan(), 0, outputPortNo);
        return new RemoveFlow(transactionIdGenerator.generate(), flowPath.getFlow().getFlowId(), cookie,
                switchId, null, criteria);
    }

    private BaseInstallFlow buildInstallIngressFlow(Flow flow, FlowPath flowPath, int outputPortNo,
                                                    TransitVlan transitVlan) {
        boolean isForward = flow.isForward(flowPath);
        SwitchId switchId = isForward ? flow.getSrcSwitch().getSwitchId() : flow.getDestSwitch().getSwitchId();
        int inPort = isForward ? flow.getSrcPort() : flow.getDestPort();
        int inVlan = isForward ? flow.getSrcVlan() : flow.getDestVlan();

        Long meterId = Optional.ofNullable(flowPath.getMeterId()).map(MeterId::getValue).orElse(null);

        return new InstallIngressFlow(transactionIdGenerator.generate(), flow.getFlowId(),
                flowPath.getCookie().getValue(), switchId, inPort,
                outputPortNo, inVlan, transitVlan.getVlan(), getOutputVlanType(flow, flowPath),
                flow.getBandwidth(), meterId);
    }

    private RemoveFlow buildRemoveIngressFlow(Flow flow, FlowPath flowPath, Integer outputPortNo) {
        boolean isForward = flow.isForward(flowPath);
        SwitchId switchId = isForward ? flow.getSrcSwitch().getSwitchId() : flow.getDestSwitch().getSwitchId();
        int inPort = isForward ? flow.getSrcPort() : flow.getDestPort();
        int inVlan = isForward ? flow.getSrcVlan() : flow.getDestVlan();

        long cookie = flowPath.getCookie().getValue();
        Long meterId = Optional.ofNullable(flowPath.getMeterId()).map(MeterId::getValue).orElse(null);
        DeleteRulesCriteria ingressCriteria = new DeleteRulesCriteria(cookie, inPort,
                inVlan, 0, outputPortNo);
        return new RemoveFlow(transactionIdGenerator.generate(), flow.getFlowId(),
                cookie, switchId, meterId, ingressCriteria);
    }

    private BaseInstallFlow makeOneSwitchRule(Flow flow, FlowPath flowPath) {
        boolean isForward = flow.isForward(flowPath);
        SwitchId switchId = isForward ? flow.getSrcSwitch().getSwitchId() : flow.getDestSwitch().getSwitchId();
        int inPort = isForward ? flow.getSrcPort() : flow.getDestPort();
        int inVlan = isForward ? flow.getSrcVlan() : flow.getDestVlan();
        int outPort = isForward ? flow.getDestPort() : flow.getSrcPort();
        int outVlan = isForward ? flow.getDestVlan() : flow.getSrcVlan();

        Long meterId = Optional.ofNullable(flowPath.getMeterId()).map(MeterId::getValue).orElse(null);
        return new InstallOneSwitchFlow(transactionIdGenerator.generate(),
                flow.getFlowId(), flowPath.getCookie().getValue(), switchId, inPort,
                outPort, inVlan, outVlan,
                getOutputVlanType(flow, flowPath), flow.getBandwidth(), meterId);
    }

    private OutputVlanType getOutputVlanType(Flow flow, FlowPath flowPath) {
        int sourceVlan = flow.isForward(flowPath) ? flow.getSrcVlan() : flow.getDestVlan();
        int dstVlan = flow.isForward(flowPath) ? flow.getDestVlan() : flow.getSrcVlan();
        if (sourceVlan == 0) {
            return dstVlan == 0 ? OutputVlanType.NONE : OutputVlanType.PUSH;
        }
        return dstVlan == 0 ? OutputVlanType.POP : OutputVlanType.REPLACE;
    }
}
