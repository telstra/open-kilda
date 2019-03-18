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
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.TransitVlanRepository;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FlowCommandFactory {
    // The default timeBasedGenerator() utilizes SecureRandom for the location part and time+sequence for the time part.
    private final NoArgGenerator transactionIdGenerator = Generators.timeBasedGenerator();
    private TransitVlanRepository transitVlanRepository;

    public FlowCommandFactory(RepositoryFactory repositoryFactory) {
        this.transitVlanRepository = repositoryFactory.createTransitVlanRepository();
    }

    /**
     * Generates install transit and egress rules commands for a flow.
     *
     * @param flow     flow to be installed.
     * @return list of commands
     */
    public List<InstallTransitFlow> createInstallTransitAndEgressRulesForFlow(Flow flow, FlowPath path) {
        if (flow.isOneSwitchFlow()) {
            return Collections.emptyList();
        }

        List<PathSegment> segments = path.getSegments();
        requireSegments(segments);

        int transitVlan = getTransitVlan(path);
        List<InstallTransitFlow> commands = new ArrayList<>();

        for (int i = 1; i < segments.size(); i++) {
            PathSegment src = segments.get(i - 1);
            PathSegment dst = segments.get(i);

            commands.add(buildInstallTransitFlow(path, src.getDestSwitch().getSwitchId(), src.getDestPort(),
                    dst.getSrcPort(), transitVlan));
        }

        PathSegment egressSegment = segments.get(segments.size() - 1);
        if (!egressSegment.getDestSwitch().getSwitchId().equals(path.getDestSwitch().getSwitchId())) {
            throw new IllegalStateException(
                    format("FlowSegment was not found for egress flow rule, flowId: %s", flow.getFlowId()));
        }

        commands.add(buildInstallEgressFlow(flow, path, egressSegment.getDestPort(), transitVlan));
        return commands;
    }

    /**
     * Generates install ingress / one switch rules commands for a flow.
     *
     * @param flow     flow to be installed.
     * @return list of commands
     */
    public BaseInstallFlow createInstallIngressRulesForFlow(Flow flow, FlowPath path) {
        if (flow.isOneSwitchFlow()) {
            return buildOneSwitchRuleCommand(flow, path);
        }
        requireSegments(path.getSegments());

        int transitVlan = getTransitVlan(path);
        PathSegment ingressSegment = path.getSegments().get(0);
        if (!ingressSegment.getSrcSwitch().getSwitchId().equals(path.getSrcSwitch().getSwitchId())) {
            throw new IllegalStateException(
                    format("FlowSegment was not found for ingress flow rule, flowId: %s", flow.getFlowId()));
        }
        return buildInstallIngressFlow(flow, path, ingressSegment.getSrcPort(), transitVlan);
    }

    /**
     * Generates remove transit and egress rules commands for a flow.
     *
     * @param flow     flow to be deleted.
     * @return list of commands
     */
    public List<RemoveFlow> createRemoveTransitAndEgressRulesForFlow(Flow flow, FlowPath path) {
        if (flow.isOneSwitchFlow()) {
            // Removing of single switch rules is done with no output port in criteria.
            return Collections.emptyList();
        }

        List<PathSegment> segments = path.getSegments();
        requireSegments(segments);
        // TODO vlan resource allready removed from DB in flow removing section
        int transitVlan = getTransitVlan(path);

        List<RemoveFlow> commands = new ArrayList<>();
        for (int i = 1; i < segments.size(); i++) {
            PathSegment src = segments.get(i - 1);
            PathSegment dst = segments.get(i);

            commands.add(buildRemoveTransitFlow(path, src.getDestSwitch().getSwitchId(), src.getDestPort(),
                    dst.getSrcPort(), transitVlan));
        }

        PathSegment egressSegment = segments.get(segments.size() - 1);
        if (!egressSegment.getDestSwitch().getSwitchId().equals(path.getDestSwitch().getSwitchId())) {
            throw new IllegalStateException(
                    format("FlowSegment was not found for egress flow rule, flowId: %s", flow.getFlowId()));
        }
        commands.add(buildRemoveEgressFlow(flow, path, egressSegment.getDestPort(), transitVlan));
        return commands;
    }

    /**
     * Generates remove ingress rules commands for a flow.
     *
     * @param flow     flow to be deleted.
     * @return list of commands
     */
    public RemoveFlow createRemoveIngressRulesForFlow(Flow flow, FlowPath path) {
        if (flow.isOneSwitchFlow()) {
            // Removing of single switch rules is done with no output port in criteria.
            return buildRemoveIngressFlow(flow, path, null);
        }
        List<PathSegment> segments = path.getSegments();
        requireSegments(segments);

        PathSegment ingressSegment = path.getSegments().get(0);
        if (!ingressSegment.getSrcSwitch().getSwitchId().equals(path.getSrcSwitch().getSwitchId())) {
            throw new IllegalStateException(
                    format("FlowSegment was not found for ingress flow rule, flowId: %s", flow.getFlowId()));
        }
        return buildRemoveIngressFlow(flow, path, ingressSegment.getSrcPort());
    }

    private void requireSegments(List<PathSegment> segments) {
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Neither one switch flow nor flow segments provided");
        }
    }

    private InstallEgressFlow buildInstallEgressFlow(Flow flow, FlowPath path, int inputPortNo, int transitVlan) {
        boolean forward = isForward(flow, path);
        int inVlan = forward ? flow.getSrcVlan() : flow.getDestVlan();
        int outPort = forward ? flow.getDestPort() : flow.getSrcPort();
        int outVlan = forward ? flow.getDestVlan() : flow.getSrcVlan();
        OutputVlanType outputVlanType = getOutputVlanType(inVlan, outVlan);

        return new InstallEgressFlow(transactionIdGenerator.generate(), flow.getFlowId(),
                path.getCookie().getValue(), path.getDestSwitch().getSwitchId(), inputPortNo, outPort,
                transitVlan, outVlan, outputVlanType);
    }

    private RemoveFlow buildRemoveEgressFlow(Flow flow, FlowPath path, int inputPortNo, int transitVlan) {
        long cookie = path.getCookie().getValue();
        boolean forward = isForward(flow, path);
        int outPort = forward ? flow.getDestPort() : flow.getSrcPort();

        DeleteRulesCriteria criteria = new DeleteRulesCriteria(cookie, inputPortNo, transitVlan, 0, outPort);
        return new RemoveFlow(transactionIdGenerator.generate(), flow.getFlowId(), cookie,
                path.getDestSwitch().getSwitchId(), null, criteria);
    }

    private InstallTransitFlow buildInstallTransitFlow(FlowPath path, SwitchId switchId, int inputPortNo,
                                                       int outputPortNo, int transitVlan) {
        return new InstallTransitFlow(transactionIdGenerator.generate(), path.getFlowId(),
                path.getCookie().getValue(),
                switchId, inputPortNo, outputPortNo, transitVlan);
    }

    private RemoveFlow buildRemoveTransitFlow(FlowPath path, SwitchId switchId, int inputPortNo,
                                              int outputPortNo, int transitVlan) {
        long cookie = path.getCookie().getValue();
        DeleteRulesCriteria criteria = new DeleteRulesCriteria(cookie, inputPortNo, transitVlan,
                0, outputPortNo);
        return new RemoveFlow(transactionIdGenerator.generate(), path.getFlowId(), cookie,
                switchId, null, criteria);
    }

    private BaseInstallFlow buildInstallIngressFlow(Flow flow, FlowPath path, int outputPortNo, int transitVlan) {
        boolean forward = isForward(flow, path);
        int inPort = forward ? flow.getSrcPort() : flow.getDestPort();
        int inVlan = forward ? flow.getSrcVlan() : flow.getDestVlan();
        int outVlan = forward ? flow.getDestVlan() : flow.getSrcVlan();
        OutputVlanType outputVlanType = getOutputVlanType(inVlan, outVlan);

        return new InstallIngressFlow(transactionIdGenerator.generate(), flow.getFlowId(),
                path.getCookie().getValue(), path.getSrcSwitch().getSwitchId(), inPort,
                outputPortNo, inVlan, transitVlan, outputVlanType,
                flow.getBandwidth(), unwrapMeterValue(path.getMeterId()));
    }

    private RemoveFlow buildRemoveIngressFlow(Flow flow, FlowPath path, Integer outputPortNo) {
        long cookie = path.getCookie().getValue();
        boolean forward = isForward(flow, path);
        int inPort = forward ? flow.getSrcPort() : flow.getDestPort();
        int inVlan = forward ? flow.getSrcVlan() : flow.getDestVlan();

        DeleteRulesCriteria ingressCriteria = new DeleteRulesCriteria(cookie, inPort, inVlan, 0, outputPortNo);
        return new RemoveFlow(transactionIdGenerator.generate(), flow.getFlowId(),
                cookie, path.getSrcSwitch().getSwitchId(), unwrapMeterValue(path.getMeterId()),
                ingressCriteria);
    }

    private InstallOneSwitchFlow buildOneSwitchRuleCommand(Flow flow, FlowPath path) {
        boolean forward = isForward(flow, path);
        int inPort = forward ? flow.getSrcPort() : flow.getDestPort();
        int inVlan = forward ? flow.getSrcVlan() : flow.getDestVlan();
        int outPort = forward ? flow.getDestPort() : flow.getSrcPort();
        int outVlan = forward ? flow.getDestVlan() : flow.getSrcVlan();
        OutputVlanType outputVlanType = getOutputVlanType(inVlan, outVlan);

        return new InstallOneSwitchFlow(transactionIdGenerator.generate(),
                flow.getFlowId(), path.getCookie().getValue(), path.getSrcSwitch().getSwitchId(), inPort,
                outPort, inVlan, outVlan,
                outputVlanType, path.getBandwidth(),
                unwrapMeterValue(path.getMeterId()));
    }

    private boolean isForward(Flow flow, FlowPath flowPath) {
        if (flowPath.getPathId().equals(flow.getForwardPathId())
                || flowPath.getPathId().equals(flow.getProtectedForwardPathId())) {
            return true;
        }
        if (flowPath.getPathId().equals(flow.getReversePathId())
                || flowPath.getPathId().equals(flow.getProtectedReversePathId())) {
            return false;
        } else {
            throw new IllegalArgumentException(format(
                    "Flow path %s doesn't correspond to the given flow %s.", flowPath.getPathId(), flow.getFlowId()));
        }
    }

    private OutputVlanType getOutputVlanType(int sourceVlanId, int destinationVlanId) {
        if (sourceVlanId == 0) {
            return destinationVlanId == 0 ? OutputVlanType.NONE : OutputVlanType.PUSH;
        }
        return destinationVlanId == 0 ? OutputVlanType.POP : OutputVlanType.REPLACE;
    }

    private int getTransitVlan(FlowPath path) {
        return transitVlanRepository.findByPathId(path.getPathId())
                .map(TransitVlan::getVlan).orElse(0);
    }

    private Long unwrapMeterValue(MeterId meterId) {
        return meterId == null ? null : meterId.getValue();
    }
}
