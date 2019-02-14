/* Copyright 2019 Telstra Open Source
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

package org.openkilda.messaging.model.grpc;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class SwitchInfoStatus {

    @JsonProperty("serial_number")
    private String serialNumber;

    @JsonProperty("uptime")
    private String uptime;

    @JsonProperty("kernel")
    private String kernel;

    @JsonProperty("mem_usage")
    private Long memUsage;

    @JsonProperty("ssd_usage")
    private Long ssdUsage;

    @JsonProperty("eth_links")
    private List<SwitchEthLinkInfoStatus> ethLinks;

    @JsonProperty("builds")
    private List<SwitchBuildInfoStatus> builds;

    @Data
    public static class SwitchEthLinkInfoStatus {
        @JsonProperty("name")
        private String name;

        @JsonProperty("status")
        private String status;
    }

    @Data
    public static class SwitchBuildInfoStatus {
        @JsonProperty("name")
        private String name;

        @JsonProperty("ope_version_hash")
        private String opeVersionHash;

        @JsonProperty("ppe_version_hash")
        private String ppeVersionHash;

        @JsonProperty("ez_driver_version")
        private String ezDriverVersion;
    }
}
