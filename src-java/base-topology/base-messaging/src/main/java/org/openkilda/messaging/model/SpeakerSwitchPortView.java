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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.io.Serializable;

/**
 * Represent details about physical switch port. Should not be used as independent entity, only as part
 * of {@link SpeakerSwitchView} definition.
 */
@Value
public class SpeakerSwitchPortView implements Serializable {
    @JsonProperty(value = "number", required = true)
    private int number;

    @NonNull
    @JsonProperty(value = "state", required = true)
    private State state;

    @Builder
    @JsonCreator
    public SpeakerSwitchPortView(
            @JsonProperty("number") int number,
            @JsonProperty("state") @NonNull State state) {
        this.number = number;
        this.state = state;
    }

    public enum State {
        UP,
        DOWN
    }
}
