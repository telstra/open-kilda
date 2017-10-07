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

package org.bitbucket.kilda.storm.topology.switchevent.activated.mock.server;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.bitbucket.kilda.storm.topology.switchevent.activated.bolt.Confirmation;

@Path("confirm/{switchId}")
@Produces(MediaType.APPLICATION_JSON)
public class OfsResource {
	
	@GET    
    public Confirmation get(@PathParam("switchId") String switchId) {
        return new Confirmation(true);
    }

}
