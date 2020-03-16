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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;
import java.util.List;

/**
 * The Class Paths.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"latency_ns", "forwardPath", "reversePath", "path", "timestamp"})
public class PathInfoData implements Serializable {

    private static final long serialVersionUID = -7372114867817832809L;


    @JsonProperty("latency_ns")
    private Integer latencyNs;

    @JsonProperty("forwardPath")
    private List<PathNode> forwardPath = null;

    @JsonProperty("reversePath")
    private List<PathNode> reversePath = null;

    @JsonProperty("path")
    private List<PathNode> path = null;

    public PathInfoData() {

    }

    @JsonCreator
    public PathInfoData(@JsonProperty("forwardPath") List<PathNode> forwardpath,
            @JsonProperty("reversePath") List<PathNode> reversepath) {
        setForwardPath(forwardpath);
        setReversePath(reversepath);
    }

    public Integer getLatencyNs() {
        return latencyNs;
    }

    public void setLatencyNs(Integer latencyNs) {
        this.latencyNs = latencyNs;
    }

    public List<PathNode> getForwardPath() {
        return forwardPath;
    }

    public void setForwardPath(List<PathNode> forwardPath) {
        this.forwardPath = forwardPath;
    }

    public List<PathNode> getReversePath() {
        return reversePath;
    }

    public void setReversePath(List<PathNode> reversePath) {
        this.reversePath = reversePath;
    }

    public List<PathNode> getPath() {
        return path;
    }

    public void setPath(List<PathNode> path) {
        this.path = path;
    }

    @Override
    public String toString() {
        return "PathInfoData [ latencyNs=" + latencyNs + ", forwardPath=" + forwardPath
                + ", reversePath=" + reversePath + ", path=" + path + "]";
    }
}
