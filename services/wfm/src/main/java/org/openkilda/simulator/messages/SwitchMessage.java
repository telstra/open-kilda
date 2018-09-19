/* Copyright 2018 Telstra Open Source
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

package org.openkilda.simulator.messages;

import static com.google.common.base.MoreObjects.toStringHelper;

import org.openkilda.messaging.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
import java.util.List;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = {
        "dpid",
        "num_of_ports",
        "links"})

public class SwitchMessage implements Serializable {
    @JsonProperty("dpid")
    private SwitchId dpid;

    @JsonProperty("num_of_ports")
    private int numOfPorts;

    @JsonProperty("links")
    private List<LinkMessage> links;

    public SwitchMessage(@JsonProperty("dpid") SwitchId dpid,
                         @JsonProperty("num_of_ports") int numOfPorts,
                         @JsonProperty("links") List<LinkMessage> links) {
        this.dpid = dpid;
        this.numOfPorts = numOfPorts;
        this.links = links;
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("dpid", dpid)
                .add("num_of_ports", numOfPorts)
                .toString();
    }

    public SwitchId getDpid() {
        return dpid;
    }

    public int getNumOfPorts() {
        return numOfPorts;
    }

    public List<LinkMessage> getLinks() {
        return links;
    }
}
