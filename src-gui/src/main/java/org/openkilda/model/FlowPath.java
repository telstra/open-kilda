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

package org.openkilda.model;

import org.openkilda.integration.model.response.PathInfoData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;

/**
 * The Class PathResponse.
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({ "flowid", "flowpath", "rerouted" })
public class FlowPath implements Serializable {

    private static final long serialVersionUID = 3039165826298801296L;

    @JsonProperty("flowid")
    private String flowid;

    @JsonProperty("flowpath")
    private PathInfoData flowpath;

    @JsonProperty("rerouted")
    private Boolean rerouted;

    @JsonCreator
    public FlowPath() {

    }

    @JsonCreator
    public FlowPath(@JsonProperty("flowid") String flowid, @JsonProperty("flowpath") PathInfoData flowpath) {
        setFlowid(flowid);
        setFlowpath(flowpath);
    }

    /**
     * Gets the flowid.
     *
     * @return the flowid
     */

    public String getFlowid() {
        return flowid;
    }

    /**
     * Sets the flowid.
     *
     * @param flowid the new flowid
     */

    public void setFlowid(final String flowid) {
        this.flowid = flowid;
    }

    /**
     * Gets the flowpath.
     *
     * @return the flowpath
     */

    public PathInfoData getFlowpath() {
        return flowpath;
    }

    /**
     * Sets the flowpath.
     *
     * @param flowpath the new flowpath
     */

    public void setFlowpath(final PathInfoData flowpath) {
        this.flowpath = flowpath;
    }

    @Override
    public String toString() {
        return "FlowPath [flowid=" + flowid + ", flowpath=" + flowpath + "]";
    }

}
