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
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.KildaFeatureToggles;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.TransitVlan;
import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.persistence.repositories.VxlanRepository;
import org.openkilda.rulemanager.DataAdapter;

import lombok.Builder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * PersistenceDataAdapter is designed for one-time use. Create new PersistenceDataAdapter for each RuleManager API
 * call. All data is cached to provide the same result for multiple getter calls and reduce DB layer calls.
 */
public class PersistenceDataAdapter implements DataAdapter {

    private final FlowPathRepository flowPathRepository;
    private final SwitchRepository switchRepository;
    private final SwitchPropertiesRepository switchPropertiesRepository;
    private final TransitVlanRepository transitVlanRepository;
    private final VxlanRepository vxlanRepository;
    private final IslRepository islRepository;
    private final KildaFeatureTogglesRepository featureTogglesRepository;

    private final Set<PathId> pathIds;
    private final Set<SwitchId> switchIds;

    private Map<PathId, Flow> flowCache;
    private Map<PathId, FlowPath> flowPathCache;
    private Map<PathId, FlowTransitEncapsulation> encapsulationCache;
    private Map<SwitchId, Switch> switchCache;
    private Map<SwitchId, SwitchProperties> switchPropertiesCache;
    private Map<SwitchId, Set<Integer>> switchIslPortsCache;
    private KildaFeatureToggles featureToggles;
    private Map<PathId, YFlow> yFlowCache;

    @Builder
    public PersistenceDataAdapter(PersistenceManager persistenceManager,
                                  Set<PathId> pathIds, Set<SwitchId> switchIds) {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        flowPathRepository = repositoryFactory.createFlowPathRepository();
        switchRepository = repositoryFactory.createSwitchRepository();
        switchPropertiesRepository = repositoryFactory.createSwitchPropertiesRepository();
        transitVlanRepository = repositoryFactory.createTransitVlanRepository();
        vxlanRepository = repositoryFactory.createVxlanRepository();
        islRepository = repositoryFactory.createIslRepository();
        featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();

        this.pathIds = pathIds;
        this.switchIds = switchIds;

        encapsulationCache = new HashMap<>();
    }

    @Override
    public Map<PathId, FlowPath> getFlowPaths() {
        if (flowPathCache == null) {
            flowPathCache = flowPathRepository.findByIds(pathIds);
        }
        return flowPathCache;
    }

    @Override
    public Flow getFlow(PathId pathId) {
        if (flowCache == null) {
            flowCache = flowPathRepository.findFlowsByPathIds(pathIds);
        }
        return flowCache.get(pathId);
    }

    @Override
    public FlowTransitEncapsulation getTransitEncapsulation(PathId pathId) {
        if (encapsulationCache.get(pathId) == null) {
            Optional<TransitVlan> vlan = transitVlanRepository.findByPathId(pathId);
            if (vlan.isPresent()) {
                encapsulationCache.put(pathId, new FlowTransitEncapsulation(vlan.get().getVlan(),
                        FlowEncapsulationType.TRANSIT_VLAN));
            } else {
                vxlanRepository.findByPathId(pathId, null)
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
        return switchIslPortsCache.get(switchId);
    }

    @Override
    public YFlow getYFlow(PathId pathId) {
        if (yFlowCache == null) {
            yFlowCache = flowPathRepository.findYFlowsByPathIds(pathIds);
        }
        return yFlowCache.get(pathId);
    }
}
