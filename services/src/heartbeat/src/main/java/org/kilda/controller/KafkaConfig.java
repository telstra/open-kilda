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

package org.bitbucket.kilda.controller;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class KafkaConfig {

	private String host;
	
	private Integer port;
	
	@Inject
	public KafkaConfig(@Named("kafka.host") String host, @Named("kafka.port") Integer port) {
		this.host = host;
		this.port = port;
	}
	
	public String url() {
		return host + ":" + port;
	}
	
}
