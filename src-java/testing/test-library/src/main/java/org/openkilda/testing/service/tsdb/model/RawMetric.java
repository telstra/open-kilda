/* Copyright 2023 Telstra Open Source
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

package org.openkilda.testing.service.tsdb.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.HashMap;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RawMetric {
    @JsonProperty("__name__")
    private String name;
    private String meterid;
    private String switchid;
    @JsonProperty("y_flow_id")
    private String yFlowId;
    private String flowid;
    private String direction;
    private String cookie;
    private String type;
    private String cookieHex;
    private String origin;
    @JsonProperty("src_switch")
    private String srcSwitch;
    @JsonProperty("dst_switch")
    private String dstSwitch;
    @JsonProperty("src_port")
    private String srcPort;
    @JsonProperty("dst_port")
    private String dstPort;
    private String inPort;
    private String outPort;
    private String tableid;
    private String port;
    private String status;

    /**
     * Converts metric object received in JSON into java HashMap.
     * @return HashMap with metrics/tags values
     */
    public HashMap<String, String> getTags() {
        HashMap<String, String> map = new HashMap<>();
        map.put("meterid", this.meterid);
        map.put("switchid", this.switchid);
        map.put("y_flow_id", this.yFlowId);
        map.put("flowid", this.flowid);
        map.put("direction", this.direction);
        map.put("name", this.name);
        map.put("cookie", this.cookie);
        map.put("type", this.type);
        map.put("cookieHex", this.cookieHex);
        map.put("origin", this.origin);
        map.put("src_switch", this.srcSwitch);
        map.put("dst_switch", this.dstSwitch);
        map.put("src_port", this.srcPort);
        map.put("dst_port", this.dstPort);
        map.put("inPort", this.inPort);
        map.put("outPort", this.outPort);
        map.put("tableid", this.tableid);
        map.put("port", this.port);
        map.put("status", this.status);
        return map;
    }
}
