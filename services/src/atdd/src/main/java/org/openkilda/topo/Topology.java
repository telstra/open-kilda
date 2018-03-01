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

import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Concrete representation of a Topology.
 *
 * It is a combination of it's raw elements as well as its graph representation
 */
public class Topology implements ITopology {

	// TODO: consider using TopoID or TopoSlug as the key, not String
    private final ConcurrentMap<String, Switch> switches;
    private final ConcurrentMap<String, Link> links;

    /** Use this constructor to build up or unserialize a topology */
    public Topology() {
        switches = new ConcurrentHashMap<>();
        links = new ConcurrentHashMap<>();
	}

    @Override
    public ConcurrentMap<String, Switch> getSwitches() {
        return switches;
    }

    @Override
    public ConcurrentMap<String, Link> getLinks() {
        return links;
    }

    public String printSwitchConnections(){
        StringBuilder sb = new StringBuilder(256);
        sb.append("Links (Abbreviated){\n");
        for (String key : new TreeSet<>(links.keySet())) {
            Link link = links.get(key);
            String s = TopoSlug.toString(link,true);
            sb.append("\t").append(s).append("\n");
        }
        sb.append("}");
        sb.trimToSize();
        return sb.toString();
    }

    /**
     * Clear the Topology, making it empty.
     */
    public void clear() {
        switches.clear();
        links.clear();
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("Topology{\n");
        sb.append("\tswitches{\n");
        for (String s : new TreeSet<>(switches.keySet())) {
            sb.append("\t\t").append(s).append("\n");
        }
        sb.append("\t}\n\tlinks=\n");
        for (String s : new TreeSet<>(links.keySet())) {
            sb.append("\t\t").append(s).append("\n");
        }
        sb.append("\t}\n}");

        return sb.toString();
    }

    /*
             * (non-Javadoc)
             *
             * @see
             * org.openkilda.topo.ITopology#equivalent(org.openkilda
             * .topo.ITopology)
             */
	@Override
	public boolean equivalent(ITopology other) {
	    // size should be the same
        if (switches.size() != other.getSwitches().size()) return false;
        if (links.size() != other.getLinks().size()) return false;

        // for now, ensure the each entryset matches (ie each is contained in the other)
        if (!switches.entrySet().containsAll(other.getSwitches().entrySet())) return false;
        if (!other.getSwitches().entrySet().containsAll(switches.entrySet())) return false;

        // ..same strategy for links..
        if (!links.entrySet().containsAll(other.getLinks().entrySet())) return false;
        if (!other.getLinks().entrySet().containsAll(links.entrySet())) return false;

		return true;
	}

    /**
     * This ignores the doc field - that isn't an essential part of equality.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Topology)) return false;

        Topology topology = (Topology) o;

        if (!switches.equals(topology.switches)) return false;
        if (!links.equals(topology.links)) return false;
        return true;
    }

    /**
     * This ignores the doc field - that isn't an essential part of equality.
     */
    @Override
    public int hashCode() {
        int result = switches.hashCode();
        result = 31 * result + links.hashCode();
        return result;
    }

    /*
     * For Testing
     */
    public static void main(String[] args) {
        Topology t1 = new Topology();
        Topology t2 = new Topology();
        System.out.println("equivalent: " + t1.equivalent(t2));
        System.out.println("equals: " + t1.equals(t2));
    }


}
