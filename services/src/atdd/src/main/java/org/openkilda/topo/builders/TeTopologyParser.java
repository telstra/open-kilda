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

package org.openkilda.topo.builders;

import static java.lang.String.format;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import lombok.Data;
import org.openkilda.topo.Link;
import org.openkilda.topo.LinkEndpoint;
import org.openkilda.topo.Switch;
import org.openkilda.topo.Topology;
import org.openkilda.topo.builders.TeTopologyParser.TopologyDto.NodeDto;
import org.openkilda.topo.exceptions.TopologyProcessingException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * A factory class that parses TopologyEngine JSON and builds a corresponding Topology entity.
 */
public class TeTopologyParser {

    public static Topology parseTopologyEngineJson(String json) {
        TopologyDto topologyDto;

        ObjectMapper mapper = new ObjectMapper();
        try {
            topologyDto = mapper.readValue(json, TopologyDto.class);
        } catch (IOException ex) {
            throw new TopologyProcessingException(format("Unable to parse the topology '%s'.", json), ex);
        }

        Map<String, Switch> switches = new HashMap<>();
        // Assemble the switches from the provided nodes.
        topologyDto.getNodes().forEach(node -> {
            String name = node.getName();
            if (Strings.isNullOrEmpty(name)) {
                throw new TopologyProcessingException("The node must have a name.");
            }

            String switchId = name.toUpperCase();
            switches.put(switchId, new Switch(switchId));
        });

        Map<String, Link> links = new HashMap<>();
        // Assemble the links from the provided outgoing_relationships.
        topologyDto.getNodes().forEach(node -> {
            String srcId = node.getName().toUpperCase();

            List<NodeDto> relations = node.getOutgoingRelationships();
            if (relations != null) {
                relations.forEach(relation -> {
                    String dstId = relation.getName().toUpperCase();

                    Link link = new Link(
                            // TODO: LinkEndpoint is immutable .. so, with no port/queue .. we should
                            // TODO: probably reuse the same endpoint. Why not?
                            new LinkEndpoint(switches.get(srcId), null, null),
                            new LinkEndpoint(switches.get(dstId), null, null)
                    );
                    links.put(link.getShortSlug(), link);
                });
            }
        });

        return new Topology(switches, links);
    }

    @Data
    static class TopologyDto {

        List<NodeDto> nodes;

        @Data
        @JsonIdentityInfo(property = "name", generator = ObjectIdGenerators.PropertyGenerator.class)
        static class NodeDto {

            String name;

            @JsonProperty("outgoing_relationships")
            List<NodeDto> outgoingRelationships;
        }
    }
}
