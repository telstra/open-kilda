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

package org.bitbucket.openkilda.topo;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.StringWriter;
import java.util.TreeSet;

/**
 * This class will convert ITopology objects to other representations
 */
public class TopologyPrinter {

    /**
     * @return A json representation of the topology. Intended for printing only.
     */
    public static final String toJson(ITopology topo, boolean pretty) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        if (pretty)
            mapper.enable(SerializationFeature.INDENT_OUTPUT);

        StringWriter sw = new StringWriter();
        JsonFactory f = mapper.getFactory();

        JsonGenerator g = f.createGenerator(sw);

        g.writeStartObject();
        // use TreeSet to sort the list
        g.writeObjectField("switches", new TreeSet<String>(topo.getSwitches().keySet()));
        g.writeObjectField("links", new TreeSet<String>(topo.getLinks().keySet()));
        g.writeEndObject();
        g.close();

        return sw.toString();
    }

    public static final String toMininetJson(ITopology topo) throws IOException {
        return toMininetJson(topo, "kilda", "floodlight", "6653");
    }


    /**
     * @return A json representation of the topology. Intended for mininet only.
     */
    public static final String toMininetJson(ITopology topo, String host, String name, String port) {
        StringBuilder sb = new StringBuilder(80);
        sb.append("{").append(makeControllerSection(host,name,port));
        sb.append(",").append(makeSwitchSection(topo));
        sb.append(",").append(makeLinkSection(topo));
        return sb.append("}").toString();
    }

    public static final String makeControllerSection(String host, String name, String port) {
        StringBuilder sb = new StringBuilder(80);
        sb.append("\"controllers\": [{\n");
        sb.append("\"host\": \"" + host + "\",\n");
        sb.append("\"name\": \"" + name + "\",\n");
        sb.append("\"port\": " + port);
        sb.append("}]\n");
        return sb.toString();
    }

    public static final String makeSwitchSection(ITopology topo) {
        StringBuilder sb = new StringBuilder(80);
        sb.append("\"switches\": [\n");
        String separator = "";
        for (String id : topo.getSwitches().keySet()) {
            sb.append(separator);
            sb.append("{\"dpid\": \"" + id + "\",");
            sb.append("\"name\": \"" + convertSwitchId2Name(id) + "\"}\n");
            separator = ", ";
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * @param id ie dpid .. 8 bytes as string, eg ".. :12:34:56:78"
     * @return the truncated, last 4 bytes, as a string "12345678"
     */
    public static final String convertSwitchId2Name(String id){
        return id.replace(":","").substring(8);
    }

    public static final String makeLinkSection(ITopology topo) {
        StringBuilder sb = new StringBuilder(80);
        sb.append("\"links\": [\n");
        String separator = "";
        for (Link link : topo.getLinks().values()) {
            sb.append(separator);
            // NB .. the code in mininet_rest uses the switch name, not id
            sb.append("{\"node1\": \"" +
                    convertSwitchId2Name(link.getSrc().getTopoSwitch().getId()) + "\",");
            sb.append( "\"node2\": \"" +
                    convertSwitchId2Name(link.getDst().getTopoSwitch().getId()) + "\"}\n");
            separator = ", ";
        }
        sb.append("]");
        return sb.toString();
    }

    public static void main(String[] args) throws IOException {
        ITopology t = TopologyBuilder.buildLinearTopo(5);
        System.out.println("t = " + t);

        System.out.println("mininet json = \n" + TopologyPrinter.toMininetJson(t));
    }

}
