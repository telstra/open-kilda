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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Defines a topology with switches, links and trafgens.
 *
 * Topology definition objects are immutable and can't be changed after creation.
 */
@Value
@NonFinal
public class TopologyDefinition {

    private List<Switch> switches;
    private List<Isl> isls;
    private List<Trafgen> trafgens;

    @JsonCreator
    public static TopologyDefinition factory(
            @JsonProperty("switches") List<Switch> switches,
            @JsonProperty("isls") List<Isl> isls,
            @JsonProperty("trafgens") List<Trafgen> trafgens) {
        return new TopologyDefinition(switches, isls, trafgens);
    }

    public List<Switch> getActiveSwitches() {
        return switches.stream()
                .filter(Switch::isActive)
                .collect(Collectors.toList());
    }

    public List<Isl> getIslsForActiveSwitches() {
        return isls.stream()
                .filter(isl -> isl.getSrcSwitch().isActive() && isl.getDstSwitch().isActive())
                .collect(Collectors.toList());
    }

    @Value
    @NonFinal
    @JsonIdentityInfo(property = "name", generator = ObjectIdGenerators.PropertyGenerator.class)
    public static class Switch {

        private String name;
        private String dpId;
        private String ofVersion;
        private Status status;

        @JsonCreator
        public static Switch factory(
                @JsonProperty("name") String name,
                @JsonProperty("dp_id") String dpId,
                @JsonProperty("of_version") String ofVersion,
                @JsonProperty("status") Status status) {
            return new Switch(name, dpId, ofVersion, status);
        }

        public boolean isActive() {
            return status == Status.Active;
        }
    }

    @Value
    @NonFinal
    public static class Isl {

        private Switch srcSwitch;
        private int srcPort;
        private Switch dstSwitch;
        private int dstPort;
        private long maxBandwidth;

        @JsonCreator
        public static Isl factory(
                @JsonProperty("src_switch") Switch srcSwitch,
                @JsonProperty("src_port") int srcPort,
                @JsonProperty("dst_switch") Switch dstSwitch,
                @JsonProperty("dst_port") int dstPort,
                @JsonProperty("max_bandwidth") long maxBandwidth) {
            return new Isl(srcSwitch, srcPort, dstSwitch, dstPort, maxBandwidth);
        }
    }

    @Value
    @NonFinal
    public static class Trafgen {

        private String name;
        private String controlEndpoint;
        private Switch switchConnected;
        private int switchPort;
        private Status status;

        @JsonCreator
        public static Trafgen factory(
                @JsonProperty("name") String name,
                @JsonProperty("control_endpoint") String controlEndpoint,
                @JsonProperty("switch") Switch switchConnected,
                @JsonProperty("switch_port") int switchPort,
                @JsonProperty("status") Status status) {
            return new Trafgen(name, controlEndpoint, switchConnected, switchPort, status);
        }
    }

    public enum Status {
        Active,
        Inactive,
        Skip
    }
}
