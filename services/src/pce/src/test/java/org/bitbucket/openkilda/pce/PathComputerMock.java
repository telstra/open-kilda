package org.bitbucket.openkilda.pce;

import org.bitbucket.openkilda.pce.model.Isl;
import org.bitbucket.openkilda.pce.model.Switch;
import org.bitbucket.openkilda.pce.provider.PathComputer;

import com.google.common.graph.MutableNetwork;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PathComputerMock implements PathComputer {
    private MutableNetwork<Switch, Isl> network;

    @Override
    public Long getWeight(Isl isl) {
        return 1L;
    }

    @Override
    public LinkedList<Isl> getPath(Switch srcSwitch, Switch dstSwitch, int bandwidth) {
        System.out.println("Get Path By Switch Instances " + bandwidth + ": " + srcSwitch + " - " + dstSwitch);

        LinkedList<Isl> path = new LinkedList<>();
        if (srcSwitch.equals(dstSwitch)) {
            return path;
        }
        Set<Switch> nodesToProcess = new HashSet<>(new HashSet<>(network.nodes()));
        Set<Switch> nodesWereProcess = new HashSet<>();
        Map<Switch, ImmutablePair<Switch, Isl>> predecessors = new HashMap<>();

        Map<Switch, Long> distances = network.nodes().stream()
                .collect(Collectors.toMap(k -> k, v -> Long.MAX_VALUE));

        distances.put(srcSwitch, 0L);

        while (!nodesToProcess.isEmpty()) {
            Switch source = nodesToProcess.stream()
                    .min(Comparator.comparingLong(distances::get))
                    .orElseThrow(() -> new IllegalArgumentException("Error: No nodes to process left"));
            nodesToProcess.remove(source);
            nodesWereProcess.add(source);

            for (Switch target : network.successors(source)) {
                if (!nodesWereProcess.contains(target)) {
                    Isl edge = network.edgesConnecting(source, target).stream()
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

        ImmutablePair<Switch, Isl> nextHop = predecessors.get(dstSwitch);
        if (nextHop == null) {
            return null;
        }
        path.add(nextHop.getRight());

        while (predecessors.get(nextHop.getLeft()) != null) {
            nextHop = predecessors.get(nextHop.getLeft());
            path.add(nextHop.getRight());
        }

        Collections.reverse(path);

        updatePathBandwidth(path, bandwidth);

        return path;
    }

    @Override
    public void updatePathBandwidth(LinkedList<Isl> path, int bandwidth) {
        System.out.println("Update Path Bandwidth " + bandwidth + ": " + path);
        path.forEach(isl -> isl.setAvailableBandwidth(isl.getAvailableBandwidth() - bandwidth));
    }

    @Override
    public PathComputer withNetwork(MutableNetwork<Switch, Isl> network) {
        this.network = network;
        return this;
    }
}
