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

package org.openkilda.topo;

import java.util.concurrent.ConcurrentMap;

/**
 * The primary interface that captures the ITopology behavior.
 */
public interface ITopology {

	/** Are the two topologies equivalent? */
	public boolean equivalent(ITopology other);
	public ConcurrentMap<String,Switch> getSwitches();
	public ConcurrentMap<String,Link> getLinks();
	public String printSwitchConnections();
}
