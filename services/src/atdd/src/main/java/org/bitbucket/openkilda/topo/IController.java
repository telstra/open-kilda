package org.bitbucket.openkilda.topo;

/**
 * Specify the external behavior of a controller
 */
public interface IController {

	// FIXME: is this the entire topology or just the switches reporting to me?
	/** @return the Topology that this controller is aware of */
	ITopology getTopology();

}
