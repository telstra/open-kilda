/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.reroute.service;

import static java.lang.String.format;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class RerouteService {

    private FlowRepository flowRepository;
    private FlowPathRepository pathRepository;

    public RerouteService(RepositoryFactory repositoryFactory) {
        this.flowRepository = repositoryFactory.createFlowRepository();
        this.pathRepository = repositoryFactory.createFlowPathRepository();
    }

    /**
     * Get list of active affected flow paths with flows.
     *
     * @param switchId switch id.
     * @param port     port.
     * @return list affected flows and flow paths.
     */
    public List<ImmutablePair<FlowPath, Flow>> getAffectedFlowPaths(SwitchId switchId, int port) {
        log.info("Get affected flow paths by node {}_{}", switchId, port);
        return pathRepository.findActiveAffectedPaths(switchId, port).stream()
                .map(path -> new ImmutablePair<>(
                        path, flowRepository.findById(path.getFlowId())
                        .orElseThrow(() -> new IllegalStateException(format("Flow %s not found", path.getFlowId())))))
                .collect(Collectors.toList());
    }

    /**
     * Get flow paths list to swap.
     *
     * @return list of flows paths.
     */
    public List<FlowPath> getPathsForSwapping(List<ImmutablePair<FlowPath, Flow>> pairs) {
        return pairs.stream()
                .filter(e -> e.getRight().isAllocateProtectedPath())
                .filter(this::filterForwardPrimaryPath)
                .map(ImmutablePair::getLeft)
                .collect(Collectors.toList());
    }

    private boolean filterForwardPrimaryPath(ImmutablePair<FlowPath, Flow> pair) {
        Flow flow = pair.getRight();
        FlowPath path = pair.getLeft();
        return Objects.equals(flow.getForwardPathId(), path.getPathId());
    }

    /**
     * Returns map with flow for reroute and set of reroute pathId.
     *
     * @return map with flow for reroute and set of reroute pathId.
     */
    public Map<Flow, Set<PathId>> groupFlowsForRerouting(List<ImmutablePair<FlowPath, Flow>> pairs) {
        return pairs.stream()
                .collect(Collectors.groupingBy(ImmutablePair::getRight,
                        Collectors.mapping(e -> e.getLeft().getPathId(), Collectors.toSet())));
    }

    /**
     * Get set of inactive flows.
     */
    public Collection<Flow> getInactiveFlows() {
        log.info("Get inactive flows");
        return flowRepository.findDownFlows();
    }
}
