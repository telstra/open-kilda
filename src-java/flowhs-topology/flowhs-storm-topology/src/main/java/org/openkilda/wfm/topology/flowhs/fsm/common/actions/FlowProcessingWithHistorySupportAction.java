/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import static java.lang.String.format;

import org.openkilda.adapter.FlowDestAdapter;
import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.adapter.FlowSourceAdapter;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.haflow.HaFlowResponse;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPath;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.persistence.repositories.HaSubFlowRepository;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.share.mappers.FlowMapper;
import org.openkilda.wfm.share.model.SpeakerRequestBuildContext;
import org.openkilda.wfm.share.model.SpeakerRequestBuildContext.PathContext;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingWithHistorySupportFsm;
import org.openkilda.wfm.topology.flowhs.mapper.HaFlowMapper;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public abstract class FlowProcessingWithHistorySupportAction<T extends FlowProcessingWithHistorySupportFsm<T, S, E, C,
        ?, ?>, S, E, C> extends HistoryRecordingAction<T, S, E, C> {
    protected final NoArgGenerator commandIdGenerator = Generators.timeBasedGenerator();

    protected final PersistenceManager persistenceManager;
    protected final TransactionManager transactionManager;
    protected final FlowRepository flowRepository;
    protected final YFlowRepository yFlowRepository;
    protected final HaFlowRepository haFlowRepository;
    protected final HaSubFlowRepository haSubFlowRepository;
    protected final FlowPathRepository flowPathRepository;
    protected final SwitchPropertiesRepository switchPropertiesRepository;
    protected final SwitchRepository switchRepository;
    protected final KildaFeatureTogglesRepository featureTogglesRepository;

    protected FlowProcessingWithHistorySupportAction(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
        this.transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.flowRepository = repositoryFactory.createFlowRepository();
        this.yFlowRepository = repositoryFactory.createYFlowRepository();
        this.haFlowRepository = repositoryFactory.createHaFlowRepository();
        this.haSubFlowRepository = repositoryFactory.createHaSubFlowRepository();
        this.flowPathRepository = repositoryFactory.createFlowPathRepository();
        this.switchPropertiesRepository = repositoryFactory.createSwitchPropertiesRepository();
        this.switchRepository = repositoryFactory.createSwitchRepository();
        this.featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
    }

    protected Flow getFlow(String flowId) {
        return flowRepository.findById(flowId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Flow %s not found", flowId)));
    }

    protected HaFlow getHaFlow(String haFlowId) {
        return haFlowRepository.findById(haFlowId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("HA-flow %s not found", haFlowId)));
    }

    protected FlowPath getFlowPath(Flow flow, PathId pathId) {
        return flow.getPath(pathId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Flow path %s not found", pathId)));
    }

    protected FlowPath getFlowPath(PathId pathId) {
        return flowPathRepository.findById(pathId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Flow path %s not found", pathId)));
    }

    protected HaFlowPath getHaFlowPath(HaFlow haFlow, PathId haPathId) {
        return haFlow.getPath(haPathId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Ha-flow %s has no ha-path %s", haFlow.getHaFlowId(), haPathId)));
    }

    protected Set<String> findFlowsIdsByEndpointWithMultiTable(SwitchId switchId, int port) {
        return new HashSet<>(flowRepository.findFlowsIdsByEndpointWithMultiTableSupport(switchId, port));
    }

    protected Set<String> findFlowIdsForMultiSwitchFlowsByEndpointWithMultiTableSupport(SwitchId switchId, int port) {
        return new HashSet<>(
                flowRepository.findFlowIdsForMultiSwitchFlowsByEndpointWithMultiTableSupport(switchId, port));
    }

    protected List<Flow> findOuterVlanMatchSharedRuleUsage(FlowEndpoint needle) {
        if (!FlowEndpoint.isVlanIdSet(needle.getOuterVlanId())) {
            return Collections.emptyList();
        }

        List<Flow> results = new ArrayList<>();
        for (Flow flow : flowRepository.findByEndpoint(needle.getSwitchId(), needle.getPortNumber())) {
            for (FlowSideAdapter flowSide : new FlowSideAdapter[]{
                    new FlowSourceAdapter(flow),
                    new FlowDestAdapter(flow)}) {
                FlowEndpoint endpoint = flowSide.getEndpoint();
                if (needle.isSwitchPortEquals(endpoint) && needle.getOuterVlanId() == endpoint.getOuterVlanId()) {
                    boolean multitableEnabled = flow.getPaths().stream()
                            .filter(path -> flow.isActualPathId(path.getPathId()))
                            .filter(path -> !path.isProtected())
                            .filter(path -> path.getSrcSwitchId().equals(endpoint.getSwitchId()))
                            .anyMatch(FlowPath::isSrcWithMultiTable);
                    if (multitableEnabled) {
                        results.add(flow);
                        break;
                    }
                }
            }
        }
        return results;
    }

    protected List<String> findServer42OuterVlanMatchSharedRuleUsage(FlowEndpoint needle) {
        // TODO(snikitin) Replace with some optimised DB request
        if (!FlowEndpoint.isVlanIdSet(needle.getOuterVlanId())) {
            return Collections.emptyList();
        }

        List<String> flowIds = new ArrayList<>();
        for (Flow flow : flowRepository.findByEndpointSwitchAndOuterVlan(
                needle.getSwitchId(), needle.getOuterVlanId())) {
            if (flow.getSrcSwitchId().equals(flow.getDestSwitchId())) {
                // skip one switch flows
                continue;
            }
            for (FlowSideAdapter flowSide : new FlowSideAdapter[]{
                    new FlowSourceAdapter(flow),
                    new FlowDestAdapter(flow)}) {
                FlowEndpoint endpoint = flowSide.getEndpoint();
                if (needle.getSwitchId().equals(endpoint.getSwitchId())
                        && needle.getOuterVlanId() == endpoint.getOuterVlanId()) {
                    boolean multitableEnabled = flow.getPaths().stream()
                            .filter(path -> flow.isActualPathId(path.getPathId()))
                            .filter(path -> !path.isProtected())
                            .filter(path -> path.getSrcSwitchId().equals(endpoint.getSwitchId()))
                            .anyMatch(FlowPath::isSrcWithMultiTable);
                    if (multitableEnabled) {
                        flowIds.add(flow.getFlowId());
                        break;
                    }
                }
            }
        }
        return flowIds;
    }

    protected Collection<Flow> getDiverseWithFlow(Flow flow) {
        return flow.getDiverseGroupId() == null ? Collections.emptyList() :
                flowRepository.findByDiverseGroupId(flow.getDiverseGroupId()).stream()
                        .filter(diverseFlow -> !flow.getFlowId().equals(diverseFlow.getFlowId())
                                || (flow.getYFlowId() != null && !flow.getYFlowId().equals(diverseFlow.getYFlowId())))
                        .collect(Collectors.toSet());
    }

    protected Switch getSwitch(SwitchId switchId) {
        return switchRepository.findById(switchId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Switch %s not found", switchId)));
    }

    protected SwitchProperties getSwitchProperties(SwitchId switchId) {
        return switchPropertiesRepository.findBySwitchId(switchId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Properties for switch %s not found", switchId)));
    }

    protected SpeakerRequestBuildContext buildBaseSpeakerContextForInstall(SwitchId srcSwitchId, SwitchId dstSwitchId) {
        if (Objects.equals(srcSwitchId, dstSwitchId)) {
            // At this moment all context props in buildBasePathContextForInstall() are about server42.
            // But server42 is not used for single switch flow.
            return SpeakerRequestBuildContext.getEmpty();
        }

        return SpeakerRequestBuildContext.builder()
                .forward(buildBasePathContextForInstall(srcSwitchId))
                .reverse(buildBasePathContextForInstall(dstSwitchId))
                .build();
    }

    private PathContext buildBasePathContextForInstall(SwitchId switchId) {
        SwitchProperties switchProperties = getSwitchProperties(switchId);
        boolean serverFlowRtt = switchProperties.isServer42FlowRtt() && isServer42FlowRttFeatureToggle();
        return PathContext.builder()
                .installServer42OuterVlanMatchSharedRule(serverFlowRtt && switchProperties.isMultiTable())
                .installServer42InputRule(serverFlowRtt && switchProperties.isMultiTable())
                .installServer42IngressRule(serverFlowRtt)
                .server42Port(switchProperties.getServer42Port())
                .server42MacAddress(switchProperties.getServer42MacAddress())
                .build();
    }

    protected boolean isServer42FlowRttFeatureToggle() {
        return featureTogglesRepository.getOrDefault().getServer42FlowRtt();
    }

    protected Message buildResponseMessage(Flow flow, CommandContext commandContext) {
        Collection<Flow> diverseWithFlow = getDiverseWithFlow(flow);
        Set<String> diverseFlows = diverseWithFlow.stream()
                .filter(f -> f.getYFlowId() == null)
                .map(Flow::getFlowId)
                .collect(Collectors.toSet());
        Set<String> diverseYFlows = diverseWithFlow.stream()
                .map(Flow::getYFlowId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> diverseHaFlows = new HashSet<>(
                haFlowRepository.findHaFlowIdsByDiverseGroupId(flow.getDiverseGroupId()));
        InfoData flowData =
                new FlowResponse(FlowMapper.INSTANCE.map(flow, diverseFlows, diverseYFlows, diverseHaFlows,
                        getFlowMirrorPaths(flow)));
        return new InfoMessage(flowData, commandContext.getCreateTime(),
                commandContext.getCorrelationId());
    }

    protected Message buildResponseMessage(HaFlow haFlow, CommandContext commandContext) {
        HaFlowResponse response = new HaFlowResponse(
                HaFlowMapper.INSTANCE.toHaFlowDto(haFlow, flowRepository, haFlowRepository));
        return new InfoMessage(response, commandContext.getCreateTime(), commandContext.getCorrelationId());
    }

    protected List<FlowMirrorPath> getFlowMirrorPaths(Flow flow) {
        return flow.getPaths().stream()
                .map(FlowPath::getFlowMirrorPointsSet)
                .flatMap(Collection::stream)
                .map(FlowMirrorPoints::getMirrorPaths)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    protected void setMirrorPointsToNewPath(PathId oldFlowPathId, PathId newFlowPathId) {
        if (newFlowPathId != null && oldFlowPathId != null) {
            transactionManager.doInTransaction(() -> flowPathRepository.findById(oldFlowPathId)
                    .ifPresent(oldPath -> flowPathRepository.findById(newFlowPathId)
                            .ifPresent(newPath ->
                                    oldPath.getFlowMirrorPointsSet().forEach(newPath::addFlowMirrorPoints))));
        }
    }

    protected Optional<String> getOrCreateFlowDiverseGroup(String diverseFlowId) throws FlowNotFoundException {
        if (StringUtils.isBlank(diverseFlowId)) {
            return Optional.empty();
        }
        Optional<String> groupId;
        if (yFlowRepository.exists(diverseFlowId)) {
            groupId = yFlowRepository.getOrCreateDiverseYFlowGroupId(diverseFlowId);
        } else if (yFlowRepository.isSubFlow(diverseFlowId)) {
            groupId = flowRepository.findById(diverseFlowId)
                    .map(Flow::getYFlowId)
                    .flatMap(yFlowRepository::getOrCreateDiverseYFlowGroupId);
        } else if (haFlowRepository.exists(diverseFlowId)) {
            groupId = haFlowRepository.getOrCreateDiverseHaFlowGroupId(diverseFlowId);
        } else if (haSubFlowRepository.exists(diverseFlowId)) {
            groupId = haSubFlowRepository.findById(diverseFlowId)
                    .map(HaSubFlow::getHaFlowId)
                    .flatMap(haFlowRepository::getOrCreateDiverseHaFlowGroupId);
        } else {
            groupId = flowRepository.getOrCreateDiverseFlowGroupId(diverseFlowId);
        }
        return Optional.of(groupId.orElseThrow(() -> new FlowNotFoundException(diverseFlowId)));
    }
}
