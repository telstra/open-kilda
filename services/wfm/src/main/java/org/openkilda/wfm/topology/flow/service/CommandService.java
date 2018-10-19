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

import org.openkilda.messaging.command.flow.BaseInstallFlow;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.openkilda.messaging.command.flow.InstallTransitFlow;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.messaging.payload.flow.OutputVlanType;
import org.openkilda.model.FlowSegment;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.FlowSegmentRepository;
import org.openkilda.wfm.converter.SwitchIdMapper;

import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class CommandService {

    private static final SwitchIdMapper SWITCH_ID_MAPPER = Mappers.getMapper(SwitchIdMapper.class);


    private PersistenceManager transactionManager;
    private FlowRepository flowRepository;
    private FlowSegmentRepository flowSegmentRepository;


    public CommandService(PersistenceManager transactionManager) {
        this.transactionManager = transactionManager;
        flowRepository = transactionManager.getRepositoryFactory().createFlowRepository();
        flowSegmentRepository = transactionManager.getRepositoryFactory().createFlowSegmentRepository();
    }

    /**
     * Generates install rules for flow.
     *
     * @param flow flow to be installed.
     * @return list of commands
     */
    public List<BaseInstallFlow> getInstallRulesForFlow(Flow flow) {
        OutputVlanType outputVlanType = getOutputVlanType(flow);
        if (Objects.equals(flow.getSourceSwitch(), flow.getDestinationSwitch())) {
            return getOneSwitchRules(flow, outputVlanType);
        }

        List<BaseInstallFlow> commands = new ArrayList<>();

        List<PathNode> pathNodes = flow.getFlowPath().getPath();
        if (pathNodes.size() == 0) {
            return commands;
        }
        commands.add(getIngressFlow(flow, outputVlanType));

        for (int i = 1; i < pathNodes.size() - 1; i += 2) {
            PathNode src = pathNodes.get(i);
            PathNode dst = pathNodes.get(i + 1);
            if (!Objects.equals(src.getSwitchId(), dst.getSwitchId())) {
                String msg = "Found non-paired node in the flowpath";
                throw new IllegalStateException(msg);
            }
            Long segmentCookie = flow.getCookie();
            if (src.getCookie() != null) {
                segmentCookie = src.getCookie();
            }
            commands.add(getIntermediateFlow(src.getSwitchId(), src.getPortNo(), dst.getPortNo(),
                    flow.getTransitVlan(), flow.getFlowId(), segmentCookie));
        }

        commands.add(getEgressFlow(flow, outputVlanType));

        return commands;
    }

    /**
     * Generates delete rules for flow.
     *
     * @param flow flow to be deleted.
     * @return list of commands
     */
    public List<RemoveFlow> getDeleteRulesForFlow(Flow flow) {
        List<RemoveFlow> commands = new ArrayList<>();
        String flowId = flow.getFlowId();
        long cookie = flow.getCookie();
        List<FlowSegment> segments = new ArrayList<>();
        flowSegmentRepository.findByFlowIdAndCookie(flowId, cookie).forEach(segments::add);

        DeleteRulesCriteria criteria = new DeleteRulesCriteria(flow.getCookie(), flow.getSourcePort(),
                flow.getSourceVlan(),0, segments.get(0).getSrcPort());
        RemoveFlow command = new RemoveFlow(1L, flowId, flow.getCookie(),
                flow.getSourceSwitch(), (long) flow.getMeterId(), criteria);
        commands.add(command);
        for (int i = 0; i < segments.size(); i++) {
            FlowSegment f = segments.get(i);
            cookie = f.getCookieId() == 0 ? flow.getCookie() : f.getCookieId();
            int dstPort = (i + 1 == segments.size()) ? flow.getDestinationPort() : segments.get(i + 1).getSrcPort();
            criteria = new DeleteRulesCriteria(cookie, f.getDestPort(), flow.getTransitVlan(),
                    0, dstPort);
            command = new RemoveFlow(1L, flowId, cookie,
                    SWITCH_ID_MAPPER.toDto(f.getDestSwitchId()),
                    0l, criteria);
            commands.add(command);
        }
        return commands;
    }

    private BaseInstallFlow getEgressFlow(Flow flow, OutputVlanType outputVlanType) {
        List<PathNode> pathNodes = flow.getFlowPath().getPath();
        int inputPort = -1;
        for (PathNode pathNode : pathNodes) {
            if (Objects.equals(flow.getDestinationSwitch(), pathNode.getSwitchId())) {
                inputPort = pathNode.getPortNo();
                break;
            }
        }
        if (inputPort == -1) {
            // TODO(tdurakov): rewrite message
            throw new IllegalStateException("Input port was not found for egress flow rule");
        }


        long egressFlowCookie = flow.getCookie();
        if (pathNodes.size() > 0) {
            int cookieIndex = pathNodes.size() - 2;
            Long cookieCandidate = pathNodes.get(cookieIndex).getCookie();
            if (cookieCandidate != null) {
                egressFlowCookie = cookieCandidate;
            }
        }

        return new InstallEgressFlow(UUID.randomUUID().getLeastSignificantBits(), flow.getFlowId(),
                egressFlowCookie, flow.getDestinationSwitch(), inputPort, flow.getDestinationPort(),
                flow.getTransitVlan(), flow.getDestinationVlan(), outputVlanType);
    }

    private BaseInstallFlow getIntermediateFlow(SwitchId switchId, int srcPortNo, int dstPortNo, int transitVlan,
                                                String flowId, long segmentCookie) {
        return new InstallTransitFlow(UUID.randomUUID().getLeastSignificantBits(), flowId, segmentCookie,
                switchId, srcPortNo, dstPortNo, transitVlan);
    }

    private BaseInstallFlow getIngressFlow(Flow flow, OutputVlanType outputVlanType) {
        List<PathNode> pathNodes = flow.getFlowPath().getPath();
        int outputPort = -1;

        for (PathNode pathNode : pathNodes) {
            if (Objects.equals(pathNode.getSwitchId(), flow.getSourceSwitch())) {
                outputPort = pathNode.getPortNo();
            }
        }
        if (outputPort == -1) {
            // TODO(tdurakov): rewrite message
            throw new IllegalStateException("Output port was not found for ingress flow rule");
        }
        return new InstallIngressFlow(UUID.randomUUID().getLeastSignificantBits(), flow.getFlowId(),
                flow.getCookie(), flow.getSourceSwitch(), flow.getSourcePort(), outputPort, flow.getSourceVlan(),
                flow.getTransitVlan(), outputVlanType, flow.getBandwidth(), (long) flow.getMeterId());
    }

    private List<BaseInstallFlow> getOneSwitchRules(Flow flow, OutputVlanType outputVlanType) {
        List<BaseInstallFlow> commands = new ArrayList();
        InstallOneSwitchFlow rule = new InstallOneSwitchFlow(UUID.randomUUID().getLeastSignificantBits(),
                flow.getFlowId(), flow.getCookie(), flow.getSourceSwitch(), flow.getSourcePort(),
                flow.getDestinationPort(), flow.getSourceVlan(), flow.getDestinationVlan(),
                outputVlanType, flow.getBandwidth(), (long) flow.getMeterId());
        commands.add(rule);
        return commands;
    }

    private OutputVlanType getOutputVlanType(Flow flow) {
        int sourceVlan = flow.getSourceVlan();
        int dstVlan = flow.getDestinationVlan();
        if (sourceVlan == 0) {
            return dstVlan == 0 ? OutputVlanType.NONE : OutputVlanType.PUSH;
        }
        return dstVlan == 0 ? OutputVlanType.POP : OutputVlanType.REPLACE;

    }

}
