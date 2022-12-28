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

package org.openkilda.rulemanager.adapter;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowMirror;
import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.KildaFeatureToggles;
import org.openkilda.model.LagLogicalPort;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.TransitVlan;
import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.LagLogicalPortRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.persistence.repositories.VxlanRepository;
import org.openkilda.rulemanager.DataAdapter;

import lombok.Builder;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * PersistenceDataAdapter is designed for one-time use. Create new PersistenceDataAdapter for each RuleManager API
 * call. All data is cached to provide the same result for multiple getter calls and reduce DB layer calls.
 */
public class PersistenceDataAdapter implements DataAdapter {

    private final FlowRepository flowRepository;
    private final FlowPathRepository flowPathRepository;
    private final FlowMirrorPathRepository flowMirrorPathRepository;
    private final SwitchRepository switchRepository;
    private final SwitchPropertiesRepository switchPropertiesRepository;
    private final TransitVlanRepository transitVlanRepository;
    private final VxlanRepository vxlanRepository;
    private final IslRepository islRepository;
    private final LagLogicalPortRepository lagLogicalPortRepository;
    private final KildaFeatureTogglesRepository featureTogglesRepository;

    private final Set<PathId> pathIds;
    private final Set<PathId> mirrorPathIds;
    private final Set<SwitchId> switchIds;

    private Map<PathId, Flow> flowCache;
    private Map<PathId, FlowPath> flowPathCache;
    private Map<PathId, FlowMirrorPath> flowMirrorPathCache;
    private Map<PathId, FlowMirror> flowMirrorCache;
    private Map<PathId, FlowMirrorPoints> flowMirrorPointsCache;
    private Map<PathId, FlowTransitEncapsulation> encapsulationCache;
    private Map<SwitchId, Switch> switchCache;
    private Map<SwitchId, SwitchProperties> switchPropertiesCache;
    private Map<SwitchId, Set<Integer>> switchIslPortsCache;
    private Map<SwitchId, List<LagLogicalPort>> switchLagPortsCache;
    private KildaFeatureToggles featureToggles;
    private Map<PathId, YFlow> yFlowCache;

    @Builder.Default
    @Deprecated
    private boolean keepMultitableForFlow = false;

    @Builder
    public PersistenceDataAdapter(
            PersistenceManager persistenceManager, Set<PathId> pathIds, Set<PathId> mirrorPathIds,
            Set<SwitchId> switchIds, boolean keepMultitableForFlow) {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        flowRepository = repositoryFactory.createFlowRepository();
        flowPathRepository = repositoryFactory.createFlowPathRepository();
        flowMirrorPathRepository = repositoryFactory.createFlowMirrorPathRepository();
        switchRepository = repositoryFactory.createSwitchRepository();
        switchPropertiesRepository = repositoryFactory.createSwitchPropertiesRepository();
        transitVlanRepository = repositoryFactory.createTransitVlanRepository();
        vxlanRepository = repositoryFactory.createVxlanRepository();
        islRepository = repositoryFactory.createIslRepository();
        lagLogicalPortRepository = repositoryFactory.createLagLogicalPortRepository();
        featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();

        this.pathIds = pathIds;
        this.mirrorPathIds = mirrorPathIds == null ? Collections.emptySet() : mirrorPathIds;
        this.switchIds = switchIds;

        encapsulationCache = new HashMap<>();

        this.keepMultitableForFlow = keepMultitableForFlow;
    }

    @Override
    public Map<PathId, FlowPath> getFlowPaths() {
        if (flowPathCache == null) {
            flowPathCache = flowPathRepository.findByIds(pathIds);
        }
        return flowPathCache;
    }

    @Override
    public Map<PathId, FlowMirrorPath> getFlowMirrorPaths() {
        if (flowMirrorPathCache == null) {
            flowMirrorPathCache = flowMirrorPathRepository.findByIds(mirrorPathIds);
        }
        return flowMirrorPathCache;
    }

    @Override
    public FlowMirror getFlowMirror(PathId pathId) {
        if (flowMirrorCache == null) {
            flowMirrorCache = flowMirrorPathRepository.findFlowsMirrorsByPathIds(mirrorPathIds);
        }
        return flowMirrorCache.get(pathId);
    }

    @Override
    public FlowMirrorPoints getFlowMirrorPoints(PathId pathId) {
        if (flowMirrorPointsCache == null) {
            flowMirrorPointsCache = flowMirrorPathRepository.findFlowsMirrorPointsByPathIds(mirrorPathIds);
        }
        return flowMirrorPointsCache.get(pathId);
    }

    @Override
    public Flow getFlow(PathId pathId) {
        if (flowCache == null) {
            flowCache = flowPathRepository.findFlowsByPathIds(pathIds);
        }
        return flowCache.get(pathId);
    }

    @Override
    public FlowTransitEncapsulation getTransitEncapsulation(PathId pathId, PathId oppositePathId) {
        if (encapsulationCache.get(pathId) == null) {
            Optional<TransitVlan> vlan = transitVlanRepository.findByPathId(pathId, oppositePathId)
                    .stream().findFirst();
            if (vlan.isPresent()) {
                encapsulationCache.put(pathId, new FlowTransitEncapsulation(vlan.get().getVlan(),
                        FlowEncapsulationType.TRANSIT_VLAN));
            } else {
                vxlanRepository.findByPathId(pathId, oppositePathId)
                        .stream().findFirst()
                        .ifPresent(vxlan -> encapsulationCache.put(pathId, new FlowTransitEncapsulation(vxlan.getVni(),
                                FlowEncapsulationType.VXLAN)));
            }
        }
        return encapsulationCache.get(pathId);
    }

    @Override
    public Switch getSwitch(SwitchId switchId) {
        if (switchCache == null) {
            switchCache = switchRepository.findByIds(switchIds);
        }
        return switchCache.get(switchId);
    }

    @Override
    public SwitchProperties getSwitchProperties(SwitchId switchId) {
        if (switchPropertiesCache == null) {
            switchPropertiesCache = switchPropertiesRepository.findBySwitchIds(switchIds);

            if (keepMultitableForFlow) {
                // Override the multitable flag with actual flow data.
                for (SwitchProperties switchProps : switchPropertiesCache.values()) {
                    SwitchId swId = switchProps.getSwitchId();
                    Switch sw = switchProps.getSwitchObj();
                    if (!switchProps.isMultiTable() && sw.supports(SwitchFeature.MULTI_TABLE)
                            && (!flowPathRepository.findBySegmentSwitchWithMultiTable(swId, true).isEmpty()
                            || !flowRepository.findByEndpointSwitchWithMultiTableSupport(swId).isEmpty())) {
                        switchPropertiesRepository.detach(switchProps);
                        switchProps.setMultiTable(true);
                    }
                }
            }
        }
        return switchPropertiesCache.get(switchId);
    }

    @Override
    public KildaFeatureToggles getFeatureToggles() {
        if (featureToggles == null) {
            featureToggles = featureTogglesRepository.getOrDefault();
        }
        return featureToggles;
    }

    @Override
    public Set<Integer> getSwitchIslPorts(SwitchId switchId) {
        if (switchIslPortsCache == null) {
            switchIslPortsCache = islRepository.findIslPortsBySwitchIds(switchIds);
        }
        return switchIslPortsCache.getOrDefault(switchId, Collections.emptySet());
    }

    @Override
    public List<LagLogicalPort> getLagLogicalPorts(SwitchId switchId) {
        if (switchLagPortsCache == null) {
            switchLagPortsCache = lagLogicalPortRepository.findBySwitchIds(switchIds);
        }
        return switchLagPortsCache.getOrDefault(switchId, Collections.emptyList());
    }

    @Override
    public YFlow getYFlow(PathId pathId) {
        if (yFlowCache == null) {
            yFlowCache = flowPathRepository.findYFlowsByPathIds(pathIds);
        }
        return yFlowCache.get(pathId);
    }
}
