package org.bitbucket.openkilda.topo;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
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
    private final ConcurrentMap<String, LinkEndpoint> endpoints;
    private final String src;

    /** Use this constructor to build up or unserialize a topology */
    public Topology() {
        this(null);
    }


    public Topology(String doc) {
        switches = new ConcurrentHashMap<>();
        links = new ConcurrentHashMap<>();
        endpoints = new ConcurrentHashMap<>();
        src = doc;
	}

    @Override
    public ConcurrentMap<String, Switch> getSwitches() {
        return switches;
    }

    @Override
    public ConcurrentMap<String, Link> getLinks() {
        return links;
    }

    @JsonIgnore
    public ConcurrentMap<String, LinkEndpoint> getEndpoints() {
        return endpoints;
    }

    @Override
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

//        sb.append("Topology{\n");
//        sb.append("Topology{\n");
//        +
//                 + switches +
//                ", links=" + links +
//                ", endpoints=" + endpoints +
//                '}';

        return sb.toString();
    }

    /*
             * (non-Javadoc)
             *
             * @see
             * org.bitbucket.openkilda.topo.ITopology#equivalent(org.bitbucket.openkilda
             * .topo.ITopology)
             */
	@Override
	public boolean equivalent(ITopology other) {
		// TODO Auto-generated method stub
		return false;
	}

	/*
	 * For Testing
	 */
	public static void main(String[] args) {
		//TODO:
		//NetworkGraph ng = new NetworkGraph();
	}

}
