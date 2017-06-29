package org.bitbucket.openkilda.topo;

/**
 * An ITopoSlug is a type of identifier for something in the topology
 */
public interface ITopoSlug {

    /**
     * @return the topology slug that uniquely identifies this entity
     */
    public String getSlug();
}
