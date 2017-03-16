package org.bitbucket.openkilda.topo;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import javafx.util.Pair;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * TopologyBuilder is a utility / factory class that can be used to build topologies.
 */
public class TopologyBuilder {

    public static final String buildJsonFromTopo(ITopology topo, boolean pretty) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        if (pretty)
            mapper.enable(SerializationFeature.INDENT_OUTPUT);

        StringWriter sw = new StringWriter();
        JsonFactory f = mapper.getFactory();

        JsonGenerator g = f.createGenerator(sw);

        g.writeStartObject();
        // use TreeSet to sort the list
        g.writeObjectField("switches", new TreeSet(topo.getSwitches().keySet()));
        g.writeObjectField("links", new TreeSet(topo.getLinks().keySet()));
        g.writeEndObject();
        g.close();

        return sw.toString();
    }



    public static final Topology buildTopoFromJson(String jsonDoc){
        ObjectMapper mapper = new ObjectMapper();

        throw new NotImplementedException();
    }

    /**
     * This implements the ability to translate a test topology into a Topology object
     *
     * @param jsonDoc A JSON doc that matches the syntax of the json files in test/resources/topology
     * @return The topology represented in the text file.
     */
    public static final Topology buildTopoFromTestJson(String jsonDoc) throws IOException {
        Topology t = new Topology(jsonDoc);
        ConcurrentMap<String, Switch> switches = t.getSwitches();
        ConcurrentMap<String, Switch> altSwitchId = new ConcurrentHashMap<>();
        ConcurrentMap<String, Link> links = t.getLinks();

        ObjectMapper mapper = new ObjectMapper();
        Map<String,ArrayList<Map<String,String>>> root = mapper.readValue(jsonDoc, Map.class);

        // populate switches first
        ArrayList<Map<String,String>> jsonSwitches = root.get("switches");
        for (Map<String,String> s : jsonSwitches) {
            String id = normalSwitchID(s.get("dpid"));
            Switch newSwitch = new Switch(id);
            switches.put(id, newSwitch);
            if (s.get("name") != null)
                altSwitchId.put(s.get("name"),newSwitch);
        }

        // now populate links
        ArrayList<Map<String,String>> jsonLinks = root.get("links");
        for (Map<String,String> l : jsonLinks) {
            String srcId = l.get("node1");
            String dstId = l.get("node2");
            Switch src = switches.get(srcId);
            if (src == null) src = altSwitchId.get(srcId);
            Switch dst = switches.get(dstId);
            if (dst == null) dst = altSwitchId.get(dstId);

            Link link = new Link(
                    new LinkEndpoint(src,null,null),
                    new LinkEndpoint(dst,null,null)
                    );
            links.put(link.getShortSlug(),link);
        }

        return t;
    }

    /**
     * In case the ID hasn't been normalized to what Mininet does.
     * This should match what mininet / kilda process - ie xx:xx.. 8 sets of xx in lower case.
     */
    private static final String normalSwitchID(String id){
        // normalize the ID ... I assume (mostly) the id is a valid mininet ID coming in.
        // now, need to make it look like what mininet / topology engine generate.

        if (id.contains(":") == false){
            StringBuilder sb = new StringBuilder(id.substring(0,2));
            for (int i = 2; i < 16; i+=2) {
                sb.append(":").append(id.substring(i,i+2));
            }
            id = sb.toString();
        }
        id = id.toLowerCase(); // mininet will do lower case ..
        System.out.println("normalSwitchID: id = " + id);
        return id;
    }

    public static final Topology buildTopoFromTopoEngineJson(String jsonDoc) throws IOException {
        Topology t = new Topology(jsonDoc);
        ConcurrentMap<String, Switch> switches = t.getSwitches();
        ConcurrentMap<String, Link> links = t.getLinks();

        // {"nodes":[{"name":"string", "outgoing_relationships": ["string",..]},..]}
        ObjectMapper mapper = new ObjectMapper();
        Map<String,ArrayList<?>> root = mapper.readValue(jsonDoc, Map.class);

        // populate switches first
        ArrayList<Map<String,?>> jsonSwitches = (ArrayList<Map<String, ?>>) root.get("nodes");
        for (Map<String,?> s : jsonSwitches) {
            String id = (String) s.get("name");
            Switch newSwitch = new Switch(id);
            switches.put(id, newSwitch);
        }

        // now populate links
        for (Map<String,?> s : jsonSwitches) {
            Switch src = switches.get(s.get("name"));

            ArrayList<String> outbound = (ArrayList<String>) s.get("outgoing_relationships");
            if (outbound != null) {
                for (String other : outbound) {
                    Switch dst = switches.get(other);
                    Link link = new Link(
                            // TODO: LinkEndpoint is immutable .. so, with no port/queue .. we should
                            // TODO: probably reuse the same endpoint. Why not?
                            new LinkEndpoint(src, null, null),
                            new LinkEndpoint(dst, null, null)
                    );
                    links.put(link.getShortSlug(), link);
                }
            }
        }

        return t;
    }



    /** buildLinearTopo models the Linear topology from Mininet */
    public static final Topology buildLinearTopo(int numSwitches){
        Topology t = new Topology();

        // Add Switches
        ConcurrentMap<String, Switch> switches = t.getSwitches();
        ConcurrentMap<String, Link> links = t.getLinks();

        for (int i = 0; i < numSwitches; i++) {
            // model the Mininet naming scheme
            String switchID = String.format("s%d",i+1);  // start with 1, not 0
            switches.put(switchID,new Switch(switchID));
        }

        // Add links between switches
        int numLinks = numSwitches-1;  // is A-->B = 2 switches, 1 link.
        for (int i = 0; i < numLinks; i++) {
            String switchID1 = String.format("s%d",i+1); // start with 1, not 0
            String switchID2 = String.format("s%d",i+2); // the next one
            Switch s1 = switches.get(switchID1);
            Switch s2 = switches.get(switchID2);
            linkSwitches(t,s1,s2);
        }
        return t;
    }

    private static final void linkSwitches(Topology t, Switch s1, Switch s2){
        Port p1 = new Port(s1,String.format("PORTAA%03d",1));
        Port p2 = new Port(s2,String.format("PORTBB%03d",1));;
        PortQueue q1 = new PortQueue(p1, String.format("QUEUE%03d",1));
        PortQueue q2 = new PortQueue(p2, String.format("QUEUE%03d",1));;

        LinkEndpoint e1 = new LinkEndpoint(q1);
        LinkEndpoint e2 = new LinkEndpoint(q2);

        Link link1 = new Link(e1,e2);
        Link link2 = new Link(e2,e1);
        t.getLinks().put(link1.getShortSlug(),link1);
        t.getLinks().put(link2.getShortSlug(),link2);
    }

    /** buildTreeTopo models the Tree topology from Mininet */
    public static final Topology buildTreeTopo(int depth, int fanout){
        TreeBuilder tb = new TreeBuilder(fanout);
        tb.build(depth);
        return tb.t;
    }

    private static final void treeDepthBuilder( int id, int depth, int fanout,
            ConcurrentMap<String, Switch> switches, ConcurrentMap<String, Link> links,
                                                   ConcurrentMap<String, LinkEndpoint> endpoints) {
        // depth == 0 means hosts ..
        // fanout, at depth == 0, means # of hosts.
        // model the Mininet naming scheme
        LinkedList<Pair<String,String>> tuples = new LinkedList<>();

        String switchID = String.format("s%d",id++);
        switches.put(switchID,new Switch(switchID));

    }

    /** buildTorusTopo models the Torus topology from Mininet */
    public static final Topology buildTorusTopo(){
        throw new NotImplementedException();
    }

    /**
     * TreeBuilder is useful for testing; it matches Mininet's TopoTree, without
     * the host connections.
     */
    private static class TreeBuilder {
        private int switchId = 1;
        private int fanout;
        private Topology t;

        TreeBuilder(int fanout){
            this.fanout = fanout;
            this.t = new Topology();
        }

        /** @return the root switch */
        private Switch build(int depth){
            LinkedList<Pair<String,String>> tuples = new LinkedList<>();
            String switchID = String.format("s%d",switchId++);
            Switch s1 = new Switch(switchID);
            t.getSwitches().put(switchID, s1);
            // the last level (depth == 1) is just a switch, so end the recursion.
            if (depth > 1) {
                for (int i = 0; i < fanout; i++) {
                    Switch s2 = build(depth - 1);
                    linkSwitches(t,s1,s2);
                }
            }
            return s1;
        }

    }

    public static void main(String[] args) throws IOException {
        ITopology t = TopologyBuilder.buildLinearTopo(5);
        System.out.println("t = " + t);

        boolean pretty = true;
        System.out.println("json = \n" + TopologyBuilder.buildJsonFromTopo(t,pretty));
    }


}
