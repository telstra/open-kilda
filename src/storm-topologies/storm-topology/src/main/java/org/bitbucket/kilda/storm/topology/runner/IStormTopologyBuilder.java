package org.bitbucket.kilda.storm.topology.runner;

import org.apache.storm.generated.StormTopology;

public interface IStormTopologyBuilder {

	String getTopologyName();

	StormTopology build();

}
