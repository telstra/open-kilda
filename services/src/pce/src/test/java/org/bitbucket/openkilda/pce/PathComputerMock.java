package org.bitbucket.openkilda.pce;

import org.bitbucket.openkilda.messaging.info.event.IslChangeType;
import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathNode;
import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;
import org.bitbucket.openkilda.pce.provider.PathComputer;

import com.google.common.graph.MutableNetwork;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PathComputerMock implements PathComputer {
    private MutableNetwork<SwitchInfoData, IslInfoData> network;

    @Override
    public Long getWeight(IslInfoData isl) {
        return 1L;
    }

    @Override
    public PathInfoData getPath(SwitchInfoData srcSwitch, SwitchInfoData dstSwitch, int bandwidth) {
        System.out.println("Get Path By Switch Instances " + bandwidth + ": " + srcSwitch + " - " + dstSwitch);

        LinkedList<IslInfoData> islInfoDataLinkedList = new LinkedList<>();
        List<PathNode> nodes = new ArrayList<>();
        PathInfoData path = new PathInfoData(0L, nodes, IslChangeType.DISCOVERED);

        if (srcSwitch.equals(dstSwitch)) {
            return path;
        }

        Set<SwitchInfoData> nodesToProcess = new HashSet<>(new HashSet<>(network.nodes()));
        Set<SwitchInfoData> nodesWereProcess = new HashSet<>();
        Map<SwitchInfoData, ImmutablePair<SwitchInfoData, IslInfoData>> predecessors = new HashMap<>();

        Map<SwitchInfoData, Long> distances = network.nodes().stream()
                .collect(Collectors.toMap(k -> k, v -> Long.MAX_VALUE));

        distances.put(srcSwitch, 0L);

        while (!nodesToProcess.isEmpty()) {
            SwitchInfoData source = nodesToProcess.stream()
                    .min(Comparator.comparingLong(distances::get))
                    .orElseThrow(() -> new IllegalArgumentException("Error: No nodes to process left"));
            nodesToProcess.remove(source);
            nodesWereProcess.add(source);

            for (SwitchInfoData target : network.successors(source)) {
                if (!nodesWereProcess.contains(target)) {
                    IslInfoData edge = network.edgesConnecting(source, target).stream()
                            .filter(isl -> isl.getAvailableBandwidth() >= bandwidth)
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Error: No enough bandwidth"));

                    Long distance = distances.get(source) + getWeight(edge);
                    if (distances.get(target) >= distance) {
                        distances.put(target, distance);
                        nodesToProcess.add(target);
                        predecessors.put(target, new ImmutablePair<>(source, edge));
                    }
                }
            }
        }

        ImmutablePair<SwitchInfoData, IslInfoData> nextHop = predecessors.get(dstSwitch);
        if (nextHop == null) {
            return null;
        }
        islInfoDataLinkedList.add(nextHop.getRight());

        while (predecessors.get(nextHop.getLeft()) != null) {
            nextHop = predecessors.get(nextHop.getLeft());
            islInfoDataLinkedList.add(nextHop.getRight());
        }

        Collections.reverse(islInfoDataLinkedList);

        islInfoDataLinkedList.forEach(node -> nodes.addAll(node.getPath()));

        updatePathBandwidth(path, bandwidth, islInfoDataLinkedList);

        return path;
    }

    @Override
    public void updatePathBandwidth(PathInfoData path, int bandwidth) {
        return;
    }

    @Override
    public PathComputer withNetwork(MutableNetwork<SwitchInfoData, IslInfoData> network) {
        this.network = network;
        return this;
    }

    private void updatePathBandwidth(PathInfoData path, int bandwidth, LinkedList<IslInfoData> islInfoDataLinkedList) {
        System.out.println("Update Path Bandwidth " + bandwidth + ": " + path);
        islInfoDataLinkedList.forEach(isl -> isl.setAvailableBandwidth(isl.getAvailableBandwidth() - bandwidth));
    }
}
