/* Copyright 2017 Telstra Open Source
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

package org.openkilda.pce.impl;

import static java.lang.String.format;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Isl;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathPair;
import org.openkilda.pce.RecoverableException;
import org.openkilda.pce.UnroutableFlowException;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.repositories.IslRepository;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Implementation of {@link PathComputer} that builds and operates over in-memory {@link AvailableNetwork}.
 * <p/>
 * The path finding algorithm is defined by provided {@link PathFinder}.
 */
@Slf4j
public class InMemoryPathComputer implements PathComputer {
    private final IslRepository islRepository;
    private final PathFinder pathFinder;

    public InMemoryPathComputer(IslRepository islRepository, PathFinder pathFinder) {
        this.islRepository = islRepository;
        this.pathFinder = pathFinder;
    }

    @Override
    public PathPair getPath(Flow flow, boolean reuseAllocatedFlowBandwidth)
            throws UnroutableFlowException, RecoverableException {

        if (flow.getSrcSwitch().getSwitchId().equals(flow.getDestSwitch().getSwitchId())) {
            log.info("No path computation for one-switch flow");
            return PathPair.builder()
                    .forward(new FlowPath(0, Collections.emptyList(), null))
                    .reverse(new FlowPath(0, Collections.emptyList(), null))
                    .build();
        }

        AvailableNetwork network = buildNetwork(flow, reuseAllocatedFlowBandwidth);

        Pair<List<Isl>, List<Isl>> biPath;
        try {
            biPath = pathFinder.findPathInNetwork(network, flow.getSrcSwitch().getSwitchId(),
                    flow.getDestSwitch().getSwitchId());
        } catch (SwitchNotFoundException e) {
            throw new UnroutableFlowException("Can't find a switch for the flow path : " + e.getMessage(),
                    e, flow.getFlowId());
        }
        if (biPath.getLeft().isEmpty() || biPath.getRight().isEmpty()) {
            throw new UnroutableFlowException(format("Can't find a path from %s to %s (bandwidth=%d%s)",
                    flow.getSrcSwitch(), flow.getDestSwitch(), flow.getBandwidth(),
                    flow.isIgnoreBandwidth() ? " ignored" : ""), flow.getFlowId());
        }

        return convertToPathPair(biPath);
    }

    private AvailableNetwork buildNetwork(Flow flow, boolean reuseAllocatedFlowBandwidth) throws RecoverableException {
        AvailableNetwork network = new AvailableNetwork();
        try {
            // Reads all active links from the database and creates representation of the network.
            Collection<Isl> links = flow.isIgnoreBandwidth() ? islRepository.findAllActive() :
                    islRepository.findActiveWithAvailableBandwidth(flow.getBandwidth());
            links.forEach(network::addLink);

            if (reuseAllocatedFlowBandwidth && !flow.isIgnoreBandwidth()) {
                // ISLs occupied by the flow (take the bandwidth already occupied by the flow into account).
                Collection<Isl> flowLinks = islRepository.findActiveAndOccupiedByFlowWithAvailableBandwidth(
                        flow.getFlowId(), flow.getBandwidth());
                flowLinks.forEach(network::addLink);
            }
        } catch (PersistenceException e) {
            throw new RecoverableException("An error from neo4j", e);
        }
        network.removeSelfLoops().reduceByCost();

        return network;
    }

    private PathPair convertToPathPair(Pair<List<Isl>, List<Isl>> biPath) {
        long latency = 0L;
        List<FlowPath.Node> forwardNodes = new LinkedList<>();
        List<FlowPath.Node> reverseNodes = new LinkedList<>();

        int seqId = 0;
        List<Isl> forwardIsl = biPath.getLeft();
        for (Isl isl : forwardIsl) {
            latency += isl.getLatency();
            forwardNodes.add(FlowPath.Node.builder()
                    .switchId(isl.getSrcSwitch().getSwitchId())
                    .portNo(isl.getSrcPort())
                    .seqId(seqId++)
                    .segmentLatency((long) isl.getLatency())
                    .build());
            forwardNodes.add(FlowPath.Node.builder()
                    .switchId(isl.getDestSwitch().getSwitchId())
                    .portNo(isl.getDestPort())
                    .seqId(seqId++)
                    .build());
        }

        seqId = 0;
        List<Isl> reverseIsl = biPath.getRight();
        for (Isl isl : reverseIsl) {
            reverseNodes.add(FlowPath.Node.builder()
                    .switchId(isl.getSrcSwitch().getSwitchId())
                    .portNo(isl.getSrcPort())
                    .seqId(seqId++)
                    .segmentLatency((long) isl.getLatency())
                    .build());
            reverseNodes.add(FlowPath.Node.builder()
                    .switchId(isl.getDestSwitch().getSwitchId())
                    .portNo(isl.getDestPort())
                    .seqId(seqId++)
                    .build());
        }

        return PathPair.builder()
                .forward(new FlowPath(latency, forwardNodes, null))
                .reverse(new FlowPath(latency, reverseNodes, null))
                .build();
    }
}
