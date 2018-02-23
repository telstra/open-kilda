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

import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.List;

/**
 * Defines a topology with switches, links and trafgens.
 *
 * Topology definition objects are immutable and can't be changed after creation.
 */
@Value
@NonFinal
public class TopologyDefinition {

    @NonNull
    private List<Switch> switches;
    @NonNull
    private List<Isl> isls;
    @NonNull
    private List<Trafgen> trafgens;
    @NonNull
    private TrafgenConfig trafgenConfig;

    @JsonCreator
    public static TopologyDefinition factory(
            @JsonProperty("switches") List<Switch> switches,
            @JsonProperty("isls") List<Isl> isls,
            @JsonProperty("trafgens") List<Trafgen> trafgens,
            @JsonProperty("trafgen_config") TrafgenConfig trafgenConfig) {
        return new TopologyDefinition(
                unmodifiableList(switches),
                unmodifiableList(isls),
                unmodifiableList(trafgens),
                trafgenConfig);
    }

    public List<Switch> getActiveSwitches() {
        return switches.stream()
                .filter(Switch::isActive)
                .collect(toList());
    }

    public List<Isl> getIslsForActiveSwitches() {
        return isls.stream()
                .filter(isl -> isl.getSrcSwitch().isActive() && isl.getDstSwitch().isActive())
                .collect(toList());
    }

    @Value
    @NonFinal
    @JsonIdentityInfo(property = "name", generator = ObjectIdGenerators.PropertyGenerator.class)
    public static class Switch {

        private String name;
        @NonNull
        private String dpId;
        @NonNull
        private String ofVersion;
        @NonNull
        private Status status;
        @NonNull
        private List<OutPort> outPorts;

        @JsonCreator
        public static Switch factory(
                @JsonProperty("name") String name,
                @JsonProperty("dp_id") String dpId,
                @JsonProperty("of_version") String ofVersion,
                @JsonProperty("status") Status status,
                @JsonProperty("out_ports") List<OutPort> outPorts) {
            return new Switch(name, dpId, ofVersion, status, outPorts);
        }

        public boolean isActive() {
            return status == Status.Active;
        }
    }

    @Value
    @NonFinal
    public static class OutPort {

        private int port;
        @NonNull
        private RangeSet<Integer> vlanRange;

        @JsonCreator
        public static OutPort factory(
                @JsonProperty("port") int port,
                @JsonProperty("vlan_range") String vlanRange) {

            return new OutPort(port, parseVlanRange(vlanRange));
        }

        private static RangeSet<Integer> parseVlanRange(String vlanRangeAsStr) {
            String[] splitRanges = vlanRangeAsStr.split(",");
            if (splitRanges.length == 0) {
                throw new IllegalArgumentException("Vlan range must be non-empty.");
            }

            ImmutableRangeSet.Builder<Integer> resultVlanRange = ImmutableRangeSet.builder();
            for (String range : splitRanges) {
                String[] boundaries = range.split("\\.\\.");
                if (boundaries.length == 0 || boundaries.length > 2) {
                    throw new IllegalArgumentException("Range " + range + " is not valid.");
                }

                int lowerBound = Integer.parseInt(boundaries[0].trim());
                if (boundaries.length == 2) {
                    int upperBound = Integer.parseInt(boundaries[1].trim());
                    resultVlanRange.add(Range.closed(lowerBound, upperBound));
                } else {
                    resultVlanRange.add(Range.closed(lowerBound, lowerBound));
                }
            }

            return resultVlanRange.build();
        }
    }

    @Value
    @NonFinal
    public static class Isl {

        @NonNull
        private Switch srcSwitch;
        private int srcPort;
        @NonNull
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

        @NonNull
        private String name;
        @NonNull
        private String controlEndpoint;
        @NonNull
        private String ifaceName;
        @NonNull
        private Switch switchConnected;
        private int switchPort;
        @NonNull
        private Status status;

        @JsonCreator
        public static Trafgen factory(
                @JsonProperty("name") String name,
                @JsonProperty("iface") String ifaceName,
                @JsonProperty("control_endpoint") String controlEndpoint,
                @JsonProperty("switch") Switch switchConnected,
                @JsonProperty("switch_port") int switchPort,
                @JsonProperty("status") Status status) {
            return new Trafgen(name, controlEndpoint, ifaceName, switchConnected, switchPort, status);
        }

        public boolean isActive() {
            return status == Status.Active;
        }
    }

    public List<Trafgen> getActiveTrafgens() {
        return trafgens.stream()
                .filter(Trafgen::isActive)
                .collect(Collectors.toList());
    }

    @Value
    @NonFinal
    public static class TrafgenConfig {
        @NonNull
        private String addressPoolBase;
        private int addressPoolPrefixLen;

        @JsonCreator
        public static TrafgenConfig factory(
                @JsonProperty("address_pool_base") String address_pool_base,
                @JsonProperty("address_pool_prefix_len") int address_pool_prefix_len) {
            return new TrafgenConfig(address_pool_base, address_pool_prefix_len);
        }
    }

    public enum Status {
        Active,
        Inactive,
        Skip
    }
}
