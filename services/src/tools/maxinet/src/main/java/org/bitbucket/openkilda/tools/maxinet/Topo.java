/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

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

	public Node getSwitch(String name) {
		// TODO - improve for large networks!
		for (Node sw : switches) {
			if (name.equals(sw.getName())) {
				return sw;
			}
		}
		
		return null;
	}
	
	public Node getHost(String name) {
		// TODO - improve for large networks!
		for (Node host : hosts) {
			if (name.equals(host.getName())) {
				return host;
			}
		}
		
		return null;
	}
	
}
