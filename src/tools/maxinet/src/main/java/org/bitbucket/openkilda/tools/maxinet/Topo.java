package org.bitbucket.openkilda.tools.maxinet;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "hosts"
    })
public class Topo {
	
	@JsonProperty("hosts")
	private List<Host> hosts;
	
	@JsonProperty("switches")
	private List<Switch> switches;
	
	@JsonProperty("links")
	private List<Link> links;
	
	public Topo host(Host host) {
	    if (hosts == null)	{
	    	hosts = new ArrayList<Host>();
	    }
	    
	    hosts.add(host);
	    
	    return this;
	}

	public Topo _switch(Switch _switch) {
	    if (switches == null)	{
	    	switches = new ArrayList<Switch>();
	    }
	    
	    switches.add(_switch);
	    
	    return this;
	}

	public Topo link(Link link) {
	    if (links == null)	{
	    	links = new ArrayList<Link>();
	    }
	    
	    links.add(link);
	    
	    return this;
	}
	
}
