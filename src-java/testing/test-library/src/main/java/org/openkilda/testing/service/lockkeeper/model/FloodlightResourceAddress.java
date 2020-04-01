/* Copyright 2020 Telstra Open Source
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

package org.openkilda.testing.service.lockkeeper.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Value;

@Value
public class FloodlightResourceAddress {

    String containerName;

    @JsonInclude(Include.NON_NULL)
    String ip;

    @JsonInclude(Include.NON_NULL)
    Integer port;

    public FloodlightResourceAddress(String containerName, String ip) {
        this.containerName = containerName;
        this.ip = ip;
        this.port = null;
    }

    public FloodlightResourceAddress(String containerName, Integer port) {
        this.containerName = containerName;
        this.ip = null;
        this.port = port;
    }

    public FloodlightResourceAddress(String containerName, String ip, Integer port) {
        this.containerName = containerName;
        this.ip = ip;
        this.port = port;
    }
}
