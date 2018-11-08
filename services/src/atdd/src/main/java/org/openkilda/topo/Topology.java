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

package org.openkilda.topo;

import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;

import lombok.Value;

import java.util.Map;
import java.util.TreeSet;

/**
 * Concrete representation of a Topology.
 * It is a combination of it's raw elements as well as its graph representation
 */
@Value
public class Topology implements ITopology {

    // TODO: consider using TopoID or TopoSlug as the key, not String
    private Map<String, Switch> switches;
    private Map<String, Link> links;

    public Topology() {
        this(emptyMap(), emptyMap());
    }

    public Topology(Map<String, Switch> switches, Map<String, Link> links) {
        this.switches = unmodifiableMap(switches);
        this.links = unmodifiableMap(links);
    }

    String printSwitchConnections() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("Links (Abbreviated){\n");
        for (String key : new TreeSet<>(links.keySet())) {
            Link link = links.get(key);
            String s = TopoSlug.toString(link, true);
            sb.append("\t").append(s).append("\n");
        }
        sb.append("}");
        sb.trimToSize();
        return sb.toString();
    }

    @Override
    public boolean equivalent(ITopology other) {
        // size should be the same
        if (switches.size() != other.getSwitches().size()) {
            return false;
        }
        if (links.size() != other.getLinks().size()) {
            return false;
        }

        // for now, ensure the each entryset matches (ie each is contained in the other)
        if (!switches.entrySet().containsAll(other.getSwitches().entrySet())) {
            return false;
        }
        if (!other.getSwitches().entrySet().containsAll(switches.entrySet())) {
            return false;
        }

        // ..same strategy for links..
        if (!links.entrySet().containsAll(other.getLinks().entrySet())) {
            return false;
        }
        if (!other.getLinks().entrySet().containsAll(links.entrySet())) {
            return false;
        }

        return true;
    }
}
