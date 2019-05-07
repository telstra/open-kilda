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

package org.openkilda.messaging.model;

import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import lombok.Builder;
import lombok.Value;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Value
public class SpeakerSwitchView implements Serializable {
    @JsonProperty(value = "datapath", required = true)
    private SwitchId datapath;

    @JsonProperty(value = "switch-socket", required = true)
    private InetSocketAddress switchSocketAddress;

    @JsonProperty(value = "speaker-socket", required = true)
    private InetSocketAddress speakerSocketAddress;

    // TODO: move to enum
    @JsonProperty(value = "OF-version")
    private String ofVersion;

    @JsonProperty(value = "description")
    private SpeakerSwitchDescription description;

    @JsonProperty(value = "features", required = true)
    private Set<Feature> features;

    @JsonProperty(value = "ports", required = true)
    private List<SpeakerSwitchPortView> ports;

    @Builder(toBuilder = true)
    @JsonCreator
    public SpeakerSwitchView(
            @JsonProperty("datapath") SwitchId datapath,
            @JsonProperty("switch-socket") InetSocketAddress switchSocketAddress,
            @JsonProperty("speaker-socket") InetSocketAddress speakerSocketAddress,
            @JsonProperty("OF-version") String ofVersion,
            @JsonProperty("description") SpeakerSwitchDescription description,
            @JsonProperty("features") Set<Feature> features,
            @JsonProperty("ports") List<SpeakerSwitchPortView> ports) {
        this.datapath = datapath;
        this.switchSocketAddress = switchSocketAddress;
        this.speakerSocketAddress = speakerSocketAddress;
        this.ofVersion = ofVersion;
        this.description = description;

        this.features = ImmutableSet.copyOf(Optional.ofNullable(features).orElse(Collections.emptySet()));
        this.ports = ImmutableList.copyOf(Optional.ofNullable(ports).orElse(Collections.emptyList()));
    }

    public enum Feature {
        METERS,
        BFD,
        BFD_REVIEW,
        ROUND_TRIP_GROUP
    }
}
