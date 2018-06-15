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

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Defines a topology with switches, links and traffgens.
 * <p>
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
    private List<TraffGen> traffGens;
    @NonNull
    private TraffGenConfig traffGenConfig;

    @JsonCreator
    public static TopologyDefinition factory(
            @JsonProperty("switches") List<Switch> switches,
            @JsonProperty("isls") List<Isl> isls,
            @JsonProperty("traffgens") List<TraffGen> traffGens,
            @JsonProperty("traffgen_config") TraffGenConfig traffGenConfig) {

        Preconditions.checkArgument(
                switches.size() == switches.stream().map(Switch::getDpId).distinct().count(),
                "Switches must have no duplicates");
        Preconditions.checkArgument(
                isls.size() == isls.stream().distinct().count(), "Isls must have no duplicates");
        Preconditions.checkArgument(
                traffGens.size() == traffGens.stream().map(TraffGen::getName).distinct().count(),
                "TraffGens must have no duplicates");

        return new TopologyDefinition(
                unmodifiableList(switches),
                unmodifiableList(isls),
                unmodifiableList(traffGens),
                traffGenConfig);
    }

    public List<Switch> getActiveSwitches() {
        return switches.stream()
                .filter(Switch::isActive)
                .collect(toList());
    }

    public Set<String> getSkippedSwitchIds() {
        return switches.stream()
                .filter(sw -> sw.getStatus() == Status.Skip)
                .map(Switch::getDpId)
                .collect(toSet());
    }

    public List<Isl> getIslsForActiveSwitches() {
        return isls.stream()
                .filter(isl ->
                        isl.getDstSwitch() != null && isl.getSrcSwitch().isActive() && isl.getDstSwitch().isActive())
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
            if (outPorts == null) {
                outPorts = emptyList();
            }

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
        private Switch dstSwitch;
        private int dstPort;
        private long maxBandwidth;
        private ASwitch aswitch;

        @JsonCreator
        public static Isl factory(
                @JsonProperty("src_switch") Switch srcSwitch,
                @JsonProperty("src_port") int srcPort,
                @JsonProperty("dst_switch") Switch dstSwitch,
                @JsonProperty("dst_port") int dstPort,
                @JsonProperty("max_bandwidth") long maxBandwidth,
                @JsonProperty("a_switch") ASwitch aswitch) {
            return new Isl(srcSwitch, srcPort, dstSwitch, dstPort, maxBandwidth, aswitch);
        }
    }

    @Value
    @NonFinal
    public static class ASwitch {

        @NonNull
        private Integer inPort;
        private Integer outPort;

        @JsonCreator
        public static ASwitch factory(
                @JsonProperty("in_port") Integer inPort,
                @JsonProperty("out_port") Integer outPort) {
            return new ASwitch(inPort, outPort);
        }
    }

    @Value
    @NonFinal
    public static class TraffGen {

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
        public static TraffGen factory(
                @JsonProperty("name") String name,
                @JsonProperty("iface") String ifaceName,
                @JsonProperty("control_endpoint") String controlEndpoint,
                @JsonProperty("switch") Switch switchConnected,
                @JsonProperty("switch_port") int switchPort,
                @JsonProperty("status") Status status) {
            return new TraffGen(name, controlEndpoint, ifaceName, switchConnected, switchPort, status);
        }

        public boolean isActive() {
            return status == Status.Active;
        }
    }

    public List<TraffGen> getActiveTraffGens() {
        return traffGens.stream()
                .filter(TraffGen::isActive)
                .filter(traffGen -> traffGen.getSwitchConnected().isActive())
                .collect(Collectors.toList());
    }

    @Value
    @NonFinal
    public static class TraffGenConfig {

        @NonNull
        private String addressPoolBase;
        private int addressPoolPrefixLen;

        @JsonCreator
        public static TraffGenConfig factory(
                @JsonProperty("address_pool_base") String addressPoolBase,
                @JsonProperty("address_pool_prefix_len") int addressPoolPrefixLen) {
            return new TraffGenConfig(addressPoolBase, addressPoolPrefixLen);
        }
    }

    public enum Status {
        Active,
        Inactive,
        Skip
    }
}
