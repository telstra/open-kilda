package org.bitbucket.openkilda.tools.maxinet;

public interface IMaxinet {
	
	IMaxinet cluster(String name, Integer minWorkers, Integer maxWorkers); 
		
	IMaxinet experiment(String name, Topo topo);

	IMaxinet setup();

	IMaxinet stop();

	IMaxinet sleep(Long sleep);

	IMaxinet run(String node, String command);

	IMaxinet host(String name, String pos);

	IMaxinet _switch(String name, Integer workerId);

	IMaxinet link(Node node1, Node node2);

}
