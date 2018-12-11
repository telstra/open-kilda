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

package org.openkilda.messaging.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.io.Serializable;

@Value
public class SpeakerSwitchDescription implements Serializable {
    @JsonProperty("manufacturer")
    private String manufacturer;

    @JsonProperty("hardware")
    private String hardware;

    @JsonProperty("software")
    private String software;

    @JsonProperty("serial-number")
    private String serialNumber;

    @JsonProperty("datapath")
    private String datapath;

    @Builder
    @JsonCreator
    public SpeakerSwitchDescription(@JsonProperty("manufacturer") String manufacturer,
                                    @JsonProperty("hardware") String hardware,
                                    @JsonProperty("software") String software,
                                    @JsonProperty("serial-number") String serialNumber,
                                    @JsonProperty("datapath") String datapath) {
        this.manufacturer = manufacturer;
        this.hardware = hardware;
        this.software = software;
        this.serialNumber = serialNumber;
        this.datapath = datapath;
    }
}
