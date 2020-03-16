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

package org.openkilda.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Data;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@JsonPropertyOrder({"flowid", "source", "destination", "maximum-bandwidth", "description",
        "last-updated"})
public class Flow {

    @JsonProperty("flowid")
    private String id;

    @JsonProperty("source")
    private FlowEndpoint source;

    @JsonProperty("destination")
    private FlowEndpoint destination;

    @JsonProperty("maximum-bandwidth")
    private int maximumBandwidth;

    @JsonProperty("description")
    private String description;

    @JsonProperty("last-updated")
    private String lastUpdated;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("timeout")
    private int timeout;
    
    @JsonProperty("diverse-flowid")
    private String diverseFlowId;
    
    @JsonProperty("allocate_protected_path")
    private boolean allocateProtectedPath;
    
    @JsonProperty("diverse_with")
    private List<String> diverseWith;
    
    
    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public FlowEndpoint getSource() {
        return source;
    }

    public void setSource(final FlowEndpoint source) {
        this.source = source;
    }

    public FlowEndpoint getDestination() {
        return destination;
    }

    public void setDestination(final FlowEndpoint destination) {
        this.destination = destination;
    }

    public int getMaximumBandwidth() {
        return maximumBandwidth;
    }

    public void setMaximumBandwidth(final int maximumBandwidth) {
        this.maximumBandwidth = maximumBandwidth;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(final String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getTimeout() {
        return timeout;
    }
    

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
    

    @Override
    public String toString() {
        return "Flow [id=" + id + ", source=" + source + ", destination=" + destination
                + ", maximumBandwidth=" + maximumBandwidth + ", description=" + description
                + ", lastUpdated=" + lastUpdated + ", status=" + status + "]";
    }

}
