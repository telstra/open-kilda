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

package org.openkilda.neo;

import org.neo4j.graphalgo.CommonEvaluators;
import org.neo4j.graphalgo.impl.shortestpath.Dijkstra;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;

import java.util.Map;

/**
 * This class facilitates basic interactions with Neo4j.
 */
public class NeoUtils {

    GraphDatabaseService graphDb;

    public NeoUtils(GraphDatabaseService graphDb) {
        this.graphDb = graphDb;
    }

    public OkNode node(String name) {
        return node().property("name", name);
    }

    /**
     * Constructs OkNode from the map.
     *
     * @param properties the properties map.
     * @return the OkNode.
     */
    public OkNode node(Map<String, Object> properties) {
        OkNode node = node();
        for (Map.Entry<String, Object> property: properties.entrySet()) {
            node.property(property.getKey(), property.getValue());
        }
        return node;
    }

    public OkNode node() {
        return new OkNode(graphDb.createNode());
    }

    public void isl(OkNode a, OkNode b) {
        a.edge(OkRels.isl, b);
        b.edge(OkRels.isl, a);
    }

    /**
     * Gets Dijkstra result.
     *
     * @param startCost the start cost
     * @param rt the relationship type
     * @param property the property
     * @param startNode the start node
     * @param endNode the end node
     * @return the Dijkstra object
     */
    public Dijkstra<Double> getDijkstra(Double startCost, RelationshipType rt, String property, OkNode startNode,
                                        OkNode endNode) {
        return new Dijkstra<>(
                startCost,
                startNode,
                endNode,
                CommonEvaluators.doubleCostEvaluator(property),
                new org.neo4j.graphalgo.impl.util.DoubleAdder(),
                new org.neo4j.graphalgo.impl.util.DoubleComparator(),
                Direction.BOTH,
                rt);
    }

    /**
     * Valid Relationships.
     */
    public enum OkRels implements RelationshipType {
        isl, flow
    }

    /**
     * Make Neo4j Relationship more fluent.
     */
    public static class OkEdge {

        public Relationship edge;

        public OkEdge(Relationship edge) {
            this.edge = edge;
        }

        public OkEdge property(String s, Object o) {
            edge.setProperty(s, o);
            return this;
        }
    }
}
