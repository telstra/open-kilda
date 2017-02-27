package org.bitbucket.openkilda.topo;

import java.util.concurrent.ConcurrentMap;

/**
 * The primary interface that captures the ITopology behavior.
 */
public interface ITopology {

	/** Are the two topologies equivalent? */
	public boolean equivalent(ITopology other);
	public ConcurrentMap<String,Switch> getSwitches();
	public ConcurrentMap<String,Link> getLinks();
	public String printSwitchConnections();
}
