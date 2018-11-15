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

package org.openkilda.topo.builders;

import static java.lang.String.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.primitives.Ints;
import org.openkilda.topo.Link;
import org.openkilda.topo.LinkEndpoint;
import org.openkilda.topo.Port;
import org.openkilda.topo.PortQueue;
import org.openkilda.topo.Switch;
import org.openkilda.topo.Topology;
import org.openkilda.topo.exceptions.TopologyProcessingException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * TestTopologyBuilder is a utility / factory class that can be used to build topologies for tests.
 */
public class TestTopologyBuilder {
    /**
     * This implements the ability to translate a test topology into a Topology object
     *
     * @param jsonDoc A JSON doc that matches the syntax of the json files in test/resources/topology
     * @return The topology represented in the text file.
     */
    @SuppressWarnings("unchecked")
    public static final Topology buildTopoFromTestJson(String jsonDoc) {
        Map<String, Switch> switches = new HashMap<>();
        Map<String, Switch> altSwitchId = new HashMap<>();
        Map<String, Link> links = new HashMap<>();

        ObjectMapper mapper = new ObjectMapper();
        Map<String,ArrayList<Map<String,String>>> root;

        try {
            root = mapper.readValue(jsonDoc, Map.class);
        } catch (IOException ex) {
            throw new TopologyProcessingException(format("Unable to parse the topology '%s'.", jsonDoc), ex);
        }

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

        return new Topology(switches, links);
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

    /**
     * buildLinearTopo models the Linear topology from Mininet
     *
     * Also, use names / ids similar to how mininet uses them
     */
    public static final Topology buildLinearTopo(int numSwitches){
        // Add Switches
        Map<String, Switch> switches = new HashMap<>();
        Map<String, Link> links = new HashMap<>();

        for (int i = 0; i < numSwitches; i++) {
            String switchID = intToSwitchId(i+1);
            switches.put(switchID, new Switch(switchID));
        }

        // Add links between switches
        int numLinks = numSwitches-1;  // is A-->B = 2 switches, 1 link.
        for (int i = 0; i < numLinks; i++) {
            Switch s1 = switches.get(intToSwitchId(i+1));
            Switch s2 = switches.get(intToSwitchId(i+2));
            linkSwitches(links,s1,s2);
        }
        return new Topology(switches, links);
    }

    public static String intToSwitchId(int i){
        byte[] ib = Ints.toByteArray(i);
        return String.format("00:00:00:00:%02x:%02x:%02x:%02x",ib[0],ib[1],ib[2],ib[3]);
    }

    private static final void linkSwitches(Map<String, Link> links, Switch s1, Switch s2){
        Port p1 = new Port(s1,String.format("PORTAA%03d",1));
        Port p2 = new Port(s2,String.format("PORTBB%03d",1));;
        PortQueue q1 = new PortQueue(p1, String.format("QUEUE%03d",1));
        PortQueue q2 = new PortQueue(p2, String.format("QUEUE%03d",1));;

        LinkEndpoint e1 = new LinkEndpoint(q1);
        LinkEndpoint e2 = new LinkEndpoint(q2);

        Link link1 = new Link(e1,e2);
        Link link2 = new Link(e2,e1);
        links.put(link1.getShortSlug(),link1);
        links.put(link2.getShortSlug(),link2);
    }

    /** buildTreeTopo models the Tree topology from Mininet */
    public static final Topology buildTreeTopo(int depth, int fanout){
        TreeBuilder tb = new TreeBuilder(fanout);
        return tb.build(depth);
    }


    /** buildTorusTopo models the Torus topology from Mininet */
    public static final Topology buildTorusTopo(){
        throw new UnsupportedOperationException();
    }

    /**
     * TreeBuilder is useful for testing; it matches Mininet's TopoTree, without
     * the host connections.
     */
    static class TreeBuilder {
        private int switchId = 1;
        private int fanout;

        private Map<String, Switch> switches = new HashMap<>();
        private Map<String, Link> links = new HashMap<>();

        TreeBuilder(int fanout){
            this.fanout = fanout;
        }

        Topology build(int depth) {
            buildSwitch(depth);

            return new Topology(switches, links);
        }

        /** @return the root switch */
        private Switch buildSwitch(int depth){
            String switchID = intToSwitchId(switchId++);
            Switch s1 = new Switch(switchID);
            switches.put(switchID, s1);
            // the last level (depth == 1) is just a switch, so end the recursion.
            if (depth > 1) {
                for (int i = 0; i < fanout; i++) {
                    Switch s2 = buildSwitch(depth - 1);
                    linkSwitches(links,s1,s2);
                }
            }
            return s1;
        }
    }
}
