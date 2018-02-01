package org.openkilda.neo;


import org.neo4j.graphalgo.CommonEvaluators;
import org.neo4j.graphalgo.impl.shortestpath.Dijkstra;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;


import java.util.Map;


/**
 * This class facilitates basic interactions with Neo4j
 */
public class NeoUtils {

    GraphDatabaseService graphDb;

    public NeoUtils (GraphDatabaseService graphDb) {
        this.graphDb = graphDb;
    }

    public OkNode node(String name)
    {
        return node().property("name",name);
    }

    public OkNode node(Map<String, Object> properties )
    {
        OkNode node = node();
        for ( Map.Entry<String, Object> property : properties.entrySet() )
        {
            node.property( property.getKey(), property.getValue() );
        }
        return node;
    }

    public OkNode node() {
        return new OkNode(graphDb.createNode());
    }

    public void isl(OkNode a, OkNode b){
        a.edge(OkRels.isl, b);
        b.edge(OkRels.isl, a);
    }


    public Dijkstra<Double> getDijkstra(Double startCost, RelationshipType rt, String property, OkNode startNode, OkNode endNode) {
        return new Dijkstra<Double>(
                startCost,
                startNode,
                endNode,
                CommonEvaluators.doubleCostEvaluator( property ),
                new org.neo4j.graphalgo.impl.util.DoubleAdder(),
                new org.neo4j.graphalgo.impl.util.DoubleComparator(),
                Direction.BOTH,
                rt );
    }



    /**
     * Valid Relationships
     */
    public enum OkRels implements RelationshipType
    {
        isl, flow
    }


    /**
     * Make Neo4j Relationship more fluent
     */
    public static class OkEdge {

        public Relationship edge;

        public OkEdge (Relationship edge){
            this.edge = edge;
        }

        public OkEdge property(String s, Object o) {
            edge.setProperty(s,o);
            return this;
        }
    }
}
