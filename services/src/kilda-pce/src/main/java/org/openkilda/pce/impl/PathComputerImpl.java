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
import org.openkilda.model.Isl;
import org.openkilda.model.Node;
import org.openkilda.model.Path;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathPair;
import org.openkilda.pce.RecoverableException;
import org.openkilda.pce.UnroutableFlowException;
import org.openkilda.pce.impl.algo.SimpleGetShortestPath;
import org.openkilda.pce.impl.model.AvailableNetwork;
import org.openkilda.pce.impl.model.SimpleIsl;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.repositories.IslRepository;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.LinkedList;
import java.util.List;

@Slf4j
public class PathComputerImpl implements PathComputer {
    private final IslRepository islRepository;

    public PathComputerImpl(IslRepository islRepository) {
        this.islRepository = islRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PathPair getPath(Flow flow, Strategy strategy, boolean allowSameFlowPath)
            throws UnroutableFlowException, RecoverableException {
        return getPath(flow, strategy, allowSameFlowPath, 35);
    }

    /**
     * Gets path between source and destination switch for specified flow.
     *
     * @param flow              the {@link Flow} instance.
     * @param strategy          the path find strategy.
     * @param allowSameFlowPath whether to allow the existing flow path to be a potential new path or not.
     * @param allowedDepth      the allowed depth for a potential path.
     * @return {@link PathPair} instances
     */
    public PathPair getPath(Flow flow, Strategy strategy, boolean allowSameFlowPath, int allowedDepth)
            throws UnroutableFlowException, RecoverableException {

        AvailableNetwork network = new AvailableNetwork();
        try {
            // Reads all active links from the database and creates representation of the network
            // in the form of {@link SimpleSwitch} and {@link SimpleIsl} between them.
            Iterable<Isl> links = islRepository.findActiveWithAvailableBandwidth(flow.isIgnoreBandwidth(),
                    flow.getBandwidth());
            links.forEach(isl -> network.addLink(isl.getSrcSwitchId(), isl.getDestSwitchId(),
                    isl.getSrcPort(), isl.getDestPort(),
                    isl.getCost(), isl.getLatency()));

            if (allowSameFlowPath) {
                // ISLs occupied by the flow.
                Iterable<Isl> flowLinks = islRepository.findOccupiedByFlow(flow.getFlowId(),
                        flow.isIgnoreBandwidth(), flow.getBandwidth());
                flowLinks.forEach(isl -> network.addLink(isl.getSrcSwitchId(), isl.getDestSwitchId(),
                        isl.getSrcPort(), isl.getDestPort(),
                        isl.getCost(), isl.getLatency()));
            }
            // FIXME(surabujin): Need to catch and trace exact exception thrown in recoverable places.
        } catch (PersistenceException e) {
            throw new RecoverableException("An error from neo4j", e);
        }

        long latency = 0L;
        List<Node> forwardNodes = new LinkedList<>();
        List<Node> reverseNodes = new LinkedList<>();

        if (!flow.getSrcSwitchId().equals(flow.getDestSwitchId())) {
            Pair<List<SimpleIsl>, List<SimpleIsl>> biPath = getPathFromNetwork(flow, network, strategy, allowedDepth);
            if (biPath.getLeft().isEmpty() || biPath.getRight().isEmpty()) {
                throw new UnroutableFlowException(format("Can't find a path from %s to %s (bandwidth=%d%s)",
                        flow.getSrcSwitchId(), flow.getDestSwitchId(), flow.getBandwidth(),
                        flow.isIgnoreBandwidth() ? " ignored" : ""), flow.getFlowId());
            }

            int seqId = 0;
            List<SimpleIsl> forwardIsl = biPath.getLeft();
            for (SimpleIsl isl : forwardIsl) {
                latency += isl.getLatency();
                forwardNodes.add(Node.builder()
                        .switchId(isl.getSrcDpid())
                        .portNo(isl.getSrcPort())
                        .seqId(seqId++)
                        .segmentLatency((long) isl.getLatency())
                        .build());
                forwardNodes.add(Node.builder()
                        .switchId(isl.getDstDpid())
                        .portNo(isl.getDstPort())
                        .seqId(seqId++)
                        .build());
            }

            seqId = 0;
            List<SimpleIsl> reverseIsl = biPath.getRight();
            for (SimpleIsl isl : reverseIsl) {
                reverseNodes.add(Node.builder()
                        .switchId(isl.getSrcDpid())
                        .portNo(isl.getSrcPort())
                        .seqId(seqId++)
                        .segmentLatency((long) isl.getLatency())
                        .build());
                reverseNodes.add(Node.builder()
                        .switchId(isl.getDstDpid())
                        .portNo(isl.getDstPort())
                        .seqId(seqId++)
                        .build());
            }
        } else {
            log.info("No path computation for one-switch flow");
        }

        return PathPair.builder()
                .forward(new Path(latency, forwardNodes))
                .reverse(new Path(latency, reverseNodes))
                .build();
    }

    /**
     * Create the query based on what the strategy is.
     */
    private Pair<List<SimpleIsl>, List<SimpleIsl>> getPathFromNetwork(Flow flow, AvailableNetwork network,
                                                                      Strategy strategy, int allowedDepth) {
        switch (strategy) {
            default:
                network.removeSelfLoops().reduceByCost();
                SimpleGetShortestPath forward = new SimpleGetShortestPath(network,
                        flow.getSrcSwitchId(), flow.getDestSwitchId(), allowedDepth);
                SimpleGetShortestPath reverse = new SimpleGetShortestPath(network,
                        flow.getDestSwitchId(), flow.getSrcSwitchId(), allowedDepth);

                List<SimpleIsl> forwardPath = forward.getPath();
                List<SimpleIsl> reversePath = reverse.getPath(forwardPath);
                //(crimi) - getPath with hint works .. you can use the next line to troubleshoot if
                // concerned about how hit is working
                //LinkedList<SimpleIsl> rPath = reverse.getPath();
                return Pair.of(forwardPath, reversePath);
        }
    }
}
