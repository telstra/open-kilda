package org.bitbucket.openkilda.tools.maxinet;

public interface IFrontend {
	
	ICluster cluster(String name, Integer minWorkers, Integer maxWorkers); 
		
	IExperiment experiment(String name, ICluster cluster, Topo topo);

}
