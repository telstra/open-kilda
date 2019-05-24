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

package org.openkilda.integration.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Data;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"speed", "path", "available_bandwidth", "state"})
@Data
public class IslLink {

    @JsonProperty("speed")
    private Integer speed;
    @JsonProperty("path")
    private List<IslPath> path = null;
    @JsonProperty("available_bandwidth")
    private Integer availableBandwidth;

    @JsonProperty("state")
    private String state;
    
    @JsonProperty("under_maintenance")
    private boolean underMaintenance;
    
    @JsonProperty("evacuate")
    private boolean evacuate;

    public Integer getSpeed() {
        return speed;
    }

    public void setSpeed(final Integer speed) {
        this.speed = speed;
    }

    public List<IslPath> getPath() {
        return path;
    }

    public void setPath(final List<IslPath> path) {
        this.path = path;
    }

    public Integer getAvailableBandwidth() {
        return availableBandwidth;
    }

    public void setAvailableBandwidth(final Integer availableBandwidth) {
        this.availableBandwidth = availableBandwidth;
    }


    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return "IslLink [speed=" + speed + ", path=" + path + ", availableBandwidth="
                + availableBandwidth + ", state=" + state + "]";
    }

}
