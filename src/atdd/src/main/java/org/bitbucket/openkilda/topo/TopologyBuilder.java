package org.bitbucket.openkilda.topo;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.util.Pair;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.lang.UnsupportedOperationException;

import com.google.common.primitives.Ints;

/**
 * TopologyBuilder is a utility / factory class that can be used to build topologies.
 */
public class TopologyBuilder {


    public static final Topology buildTopoFromJson(String jsonDoc){
        ObjectMapper mapper = new ObjectMapper();

        throw new UnsupportedOperationException();
    }

    /**
     * This implements the ability to translate a test topology into a Topology object
     *
     * @param jsonDoc A JSON doc that matches the syntax of the json files in test/resources/topology
     * @return The topology represented in the text file.
     */
    @SuppressWarnings("unchecked")
    public static final Topology buildTopoFromTestJson(String jsonDoc) throws IOException {
        Topology t = new Topology(jsonDoc);
        ConcurrentMap<String, Switch> switches = t.getSwitches();
        ConcurrentMap<String, Switch> altSwitchId = new ConcurrentHashMap<>();
        ConcurrentMap<String, Link> links = t.getLinks();

        ObjectMapper mapper = new ObjectMapper();
        Map<String,ArrayList<Map<String,String>>> root =
                mapper.readValue(jsonDoc, Map.class);

        // populate switches first
        ArrayList<Map<String,String>> jsonSwitches = root.get("switches");
        for (Map<String,String> s : jsonSwitches) {
            String id = normalSwitchID(s.get("dpid"));
            Switch newSwitch = new Switch(id);
            switches.put(id, newSwitch);
            if (s.get("dpid") != null)
                altSwitchId.put(s.get("dpid"),newSwitch);
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
        return id;
    }

    @SuppressWarnings("unchecked")
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
            String id = (String) s.get("dpid");
            id = id.toUpperCase();
            Switch newSwitch = new Switch(id);
            switches.put(id, newSwitch);
        }

        // now populate links
        for (Map<String,?> s : jsonSwitches) {
            // TODO: srcId could be NULL in a malformed json ... do the right thing here.
            String srcId = (String) s.get("dpid");
            srcId = srcId.toUpperCase();
            Switch src = switches.get(srcId);

            ArrayList<String> outbound = (ArrayList<String>) s.get("outgoing-relationships");
            if (outbound != null) {
                for (String other : outbound) {
                    other = other.toUpperCase();
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



    /**
     * buildLinearTopo models the Linear topology from Mininet
     *
     * Also, use names / ids similar to how mininet uses them
     */
    public static final Topology buildLinearTopo(int numSwitches){
        Topology t = new Topology();

        // Add Switches
        ConcurrentMap<String, Switch> switches = t.getSwitches();
        ConcurrentMap<String, Link> links = t.getLinks();

        for (int i = 0; i < numSwitches; i++) {
            String switchID = intToSwitchId(i+1);
            switches.put(switchID, new Switch(switchID));
        }

        // Add links between switches
        int numLinks = numSwitches-1;  // is A-->B = 2 switches, 1 link.
        for (int i = 0; i < numLinks; i++) {
            Switch s1 = switches.get(intToSwitchId(i+1));
            Switch s2 = switches.get(intToSwitchId(i+2));
            linkSwitches(t,s1,s2);
        }
        return t;
    }

    private static final String intToSwitchId(int i){
        byte[] ib = Ints.toByteArray(i);
        return String.format("DE:AD:BE:EF:%02x:%02x:%02x:%02x",ib[0],ib[1],ib[2],ib[3]);
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


    /** buildTorusTopo models the Torus topology from Mininet */
    public static final Topology buildTorusTopo(){
        throw new UnsupportedOperationException();
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
            String switchID = intToSwitchId(switchId++);
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
        System.out.println("json = \n" + TopologyPrinter.toJson(t,pretty));
    }


}
