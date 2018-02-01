package org.openkilda.neo;

import org.neo4j.graphdb.*;

import java.util.Map;

/**
 * Make Neo4j Node more fluent.
 * And Make it so that Dijkstra can work if we desired to run it from here -- the issue is the
 * desire to filter the kinds of relationships to consider (ie cost, active)
 */
public class OkNode implements Node {

    private Node n;

    public OkNode(Node node){
        this.n = node;
    }

    public OkNode property(String s, Object o) {
        n.setProperty(s,o);
        return this;
    }

    public NeoUtils.OkEdge edge(RelationshipType r, OkNode other) {
        return new NeoUtils.OkEdge(this.n.createRelationshipTo(other.n,r));
    }


    /*
     * Override methods - just delegate to the source node
     */

    @Override
    public long getId() {
        return n.getId();
    }

    @Override
    public void delete() {
        n.delete();
    }

    @Override
    public Iterable<Relationship> getRelationships() {
        return n.getRelationships();
    }

    @Override
    public boolean hasRelationship() {
        return n.hasRelationship();
    }

    @Override
    public Iterable<Relationship> getRelationships(RelationshipType... types) {
        return null;
    }

    @Override
    public Iterable<Relationship> getRelationships(Direction direction, RelationshipType... types) {
        return null;
    }

    @Override
    public boolean hasRelationship(RelationshipType... types) {
        return false;
    }

    @Override
    public boolean hasRelationship(Direction direction, RelationshipType... types) {
        return false;
    }

    @Override
    public Iterable<Relationship> getRelationships(Direction dir) {
        return null;
    }

    @Override
    public boolean hasRelationship(Direction dir) {
        return false;
    }

    @Override
    public Iterable<Relationship> getRelationships(RelationshipType type, Direction dir) {
        return null;
    }

    @Override
    public boolean hasRelationship(RelationshipType type, Direction dir) {
        return false;
    }

    @Override
    public Relationship getSingleRelationship(RelationshipType type, Direction dir) {
        return null;
    }

    @Override
    public Relationship createRelationshipTo(Node otherNode, RelationshipType type) {
        return null;
    }

    @Override
    public Iterable<RelationshipType> getRelationshipTypes() {
        return null;
    }

    @Override
    public int getDegree() {
        return 0;
    }

    @Override
    public int getDegree(RelationshipType type) {
        return 0;
    }

    @Override
    public int getDegree(Direction direction) {
        return 0;
    }

    @Override
    public int getDegree(RelationshipType type, Direction direction) {
        return 0;
    }

    @Override
    public void addLabel(Label label) {

    }

    @Override
    public void removeLabel(Label label) {

    }

    @Override
    public boolean hasLabel(Label label) {
        return false;
    }

    @Override
    public Iterable<Label> getLabels() {
        return null;
    }

    @Override
    public GraphDatabaseService getGraphDatabase() {
        return null;
    }

    @Override
    public boolean hasProperty(String key) {
        return false;
    }

    @Override
    public Object getProperty(String key) {
        return null;
    }

    @Override
    public Object getProperty(String key, Object defaultValue) {
        return null;
    }

    @Override
    public void setProperty(String key, Object value) {

    }

    @Override
    public Object removeProperty(String key) {
        return null;
    }

    @Override
    public Iterable<String> getPropertyKeys() {
        return null;
    }

    @Override
    public Map<String, Object> getProperties(String... keys) {
        return null;
    }

    @Override
    public Map<String, Object> getAllProperties() {
        return null;
    }
}
