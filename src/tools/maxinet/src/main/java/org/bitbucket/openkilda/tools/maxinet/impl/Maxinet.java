package org.bitbucket.openkilda.tools.maxinet.impl;

import java.util.Stack;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bitbucket.openkilda.tools.maxinet.Host;
import org.bitbucket.openkilda.tools.maxinet.IMaxinet;
import org.bitbucket.openkilda.tools.maxinet.Link;
import org.bitbucket.openkilda.tools.maxinet.Node;
import org.bitbucket.openkilda.tools.maxinet.Switch;
import org.bitbucket.openkilda.tools.maxinet.Topo;
import org.bitbucket.openkilda.tools.maxinet.exception.MaxinetException;

@Named
public class Maxinet implements IMaxinet {

	private static final Logger logger = LoggerFactory.getLogger(Maxinet.class);
	
	@Inject
	private WebTarget webTarget;
	
	private Cluster cluster;
	
	private Topo topo;
	
	private Experiment experiment;
	
	// all the commands we have run
	private Stack<Command> commands;
	
	@Override
	public IMaxinet cluster(String name, Integer minWorkers, Integer maxWorkers) {		
	    logger.debug("starting cluster " + this);
	    
        Response response = webTarget.path("frontend/cluster").request(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON).post(Entity.json(new Cluster(name, minWorkers, maxWorkers)));
		this.cluster = response.readEntity(Cluster.class);
		return this;
	}

	@Override
	public IMaxinet experiment(String name, Topo topo) {		
		logger.debug("creating experiment " + experiment);

		Response response = webTarget.path("frontend/experiment").request(MediaType.APPLICATION_JSON).post(Entity.json(new Experiment(name, cluster, topo)));
		this.experiment = response.readEntity(Experiment.class);
		this.topo = topo;
		
		return this;
	}
	
	@Override
	public IMaxinet setup() {
		logger.debug("setting up experiment " + this);
		
		Response response = webTarget.path("experiment/setup/").path(experiment.getName()).request(MediaType.APPLICATION_JSON).get();
		this.experiment = response.readEntity(Experiment.class);
		return this;
	}

	@Override
	public IMaxinet stop() {
		logger.debug("setting up experiment " + this);
		
		Response response = webTarget.path("experiment/stop/").path(experiment.getName()).request(MediaType.APPLICATION_JSON).get();
		this.experiment = response.readEntity(Experiment.class);
		return this;
	}
	
	@Override
	public IMaxinet sleep(Long sleep) {
		logger.debug("sleep for " + sleep + "ms");
		
		try {
			Thread.sleep(4000);
		} catch (InterruptedException e) {
			throw new MaxinetException("sleep failed ", e);
		}
		
		return this;
	}
	
	@Override
	public IMaxinet run(String node, String command) {
		logger.debug("run command " + command + " on node " + node);
		
		Response response = webTarget.path("experiment/run/").path(experiment.getName() + "/").path(node + "/").path(command).request(MediaType.APPLICATION_JSON).get();
		Command cmd = response.readEntity(Command.class);
		logger.debug("output " + cmd.getOutput());
		pushCommand(cmd);
		
		return this;
	}
	
	@Override
	public IMaxinet host(String name, String pos) {
		logger.debug("creating host " + name);

		Response response = webTarget.path("experiment/").path(experiment.getName() + "/").path("host").request(MediaType.APPLICATION_JSON).post(Entity.json(new Host(name, pos)));
		Host host = response.readEntity(Host.class);
		this.topo.host(host);
		
		return this;		
	}

	@Override
	public IMaxinet _switch(String name, Integer workerId) {
		logger.debug("creating switch " + name);

		Response response = webTarget.path("experiment/").path(experiment.getName() + "/").path("switch").request(MediaType.APPLICATION_JSON).post(Entity.json(new Switch(name, workerId)));
		Switch _switch = response.readEntity(Switch.class);
		this.topo._switch(_switch);
		
		return this;		
	}
	
	@Override
	public IMaxinet link(Node node1, Node node2) {
		logger.debug("creating link from " + node1.getName() + " to " + node2.getName());

		Response response = webTarget.path("experiment/").path(experiment.getName() + "/").path("link").request(MediaType.APPLICATION_JSON).post(Entity.json(new Link(node1, node2)));
		Link link = response.readEntity(Link.class);
		this.topo.link(link);
		
		return this;		
	}
	
	private void pushCommand(Command command) {
		if (commands == null) {
			commands = new Stack<Command>();
		}
		
		commands.push(command);
	}

}
