/* Copyright 2021 Telstra Open Source
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

import lombok.Data;

@Data
public class ChangeSwIpRequest {

    String region;

    String containerName;

    String ip;

    String newIp;

    public ChangeSwIpRequest(String region, String containerName, String ip, String newIp) {
        this.region = region;
        this.containerName = containerName;
        this.ip = ip;
        this.newIp = newIp;
    }
}
