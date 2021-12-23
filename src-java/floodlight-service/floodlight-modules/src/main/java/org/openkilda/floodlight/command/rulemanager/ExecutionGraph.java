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

package org.openkilda.floodlight.command.rulemanager;

import com.google.common.annotations.VisibleForTesting;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ExecutionGraph {
    private Map<String, Node> nodes = new HashMap<>();
    private boolean ready;
    private List<String> topologicalOrder = new ArrayList<>();
    @VisibleForTesting
    List<List<String>> stages = new ArrayList<>();
    private int currentStage = 0;

    /**
     * Returns current stage.
     */
    public List<String> getCurrent() {
        if (!ready) {
            buildStages();
        }
        if (currentStage >= stages.size()) {
            return Collections.emptyList();
        }

        return stages.get(currentStage);
    }

    /**
     * Next stage.
     */
    public boolean nextStage() {
        if (!ready) {
            buildStages();
        }
        currentStage += 1;
        return currentStage < stages.size();

    }

    /**
     * Returns uuid of nodes to depend on.
     */
    public List<String> getNodeDependsOn(String uuid) {
        if (!nodes.containsKey(uuid)) {
            throw new IllegalArgumentException(String.format("Unknown task uuid=%s", uuid));
        }
        return nodes.get(uuid).getDepends().stream()
                .map(Node::getUuid).collect(Collectors.toList());
    }

    @VisibleForTesting
    void buildStages() {
        topologicalSort();
        Map<Integer, List<String>> stagesMap = new HashMap<>();
        for (String uuid : topologicalOrder) {
            Node node = nodes.get(uuid);
            for (Node dep : node.getDepends()) {
                node.order = Math.max(node.order, dep.order + 1);
            }
            stagesMap.computeIfAbsent(node.order, x -> new ArrayList<>()).add(uuid);
        }
        for (int i = 0; i < stagesMap.size(); i++) {
            stages.add(stagesMap.get(i));
        }
        ready = true;
    }

    private void topologicalSort() {
        for (Node node : nodes.values()) {
            visit(node);
        }
    }

    private void visit(Node node) {
        if (node.color == NodeColor.BLACK) {
            return;
        } else if (node.color == NodeColor.GREY) {
            throw new IllegalStateException("Execution Graph has cycles");
        } else {
            node.color = NodeColor.GREY;
            for (Node outNode : node.getDepends()) {
                visit(outNode);
            }
            node.color = NodeColor.BLACK;
            topologicalOrder.add(node.uuid);
        }
    }

    /**
     * Adds task to graph.
     */
    public void add(String uuid, Collection<String> dependsOn) {
        if (ready) {
            throw new IllegalStateException("Graph is already computed.");
        }
        Node uuidNode = ensureNode(uuid);
        for (String dep : dependsOn) {
            Node depNode = ensureNode(dep);
            uuidNode.getDepends().add(depNode);
        }

    }

    private Node ensureNode(String uuid) {
        return nodes.computeIfAbsent(uuid, x -> new Node(uuid));
    }

    @Data
    private static class Node {
        private String uuid;
        private Set<Node> depends = new HashSet<>();
        private NodeColor color = NodeColor.WHITE;
        private int order;

        public Node(String uuid) {
            this.uuid = uuid;
        }
    }

    private enum NodeColor {
        WHITE,
        GREY,
        BLACK
    }
}

