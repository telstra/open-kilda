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

package org.openkilda.tools.maxinet;

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
