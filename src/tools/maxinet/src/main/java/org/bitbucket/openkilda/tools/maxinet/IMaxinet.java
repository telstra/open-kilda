package org.bitbucket.openkilda.tools.maxinet;

public interface IMaxinet {
	
	IMaxinet cluster(String name, Integer minWorkers, Integer maxWorkers); 
		
	IMaxinet experiment(String name, Topo topo);

	IMaxinet setup();

	IMaxinet stop();

}
