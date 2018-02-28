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

package org.openkilda.atdd.staging.model.topology;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import lombok.Data;

import java.util.List;

@Data
public class TopologyDefinition {

    private List<Switch> switches;
    private List<Isl> isls;
    private List<Trafgen> trafgens;

    @Data
    public static class Switch {

        private String name;
        private String dpId;
        private String ofVersion;
        private Status status;
    }

    @Data
    public static class Isl {

        @JsonIdentityInfo(property = "name", generator = ObjectIdGenerators.PropertyGenerator.class)
        private Switch srcSwitch;
        private int srcPort;
        @JsonIdentityInfo(property = "name", generator = ObjectIdGenerators.PropertyGenerator.class)
        private Switch dstSwitch;
        private int dstPort;
        private long maxBandwidth;
    }

    @Data
    public static class Trafgen {

        private String name;
        private String controlEndpoint;
        @JsonProperty("switch")
        @JsonIdentityInfo(property = "name", generator = ObjectIdGenerators.PropertyGenerator.class)
        private Switch switchConnected;
        private int switchPort;
        private Status status;
    }

    public enum Status {
        Active,
        Inactive,
        Skip
    }
}
