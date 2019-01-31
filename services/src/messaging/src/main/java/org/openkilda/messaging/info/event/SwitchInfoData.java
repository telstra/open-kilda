/* Copyright 2017 Telstra Open Source
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

package org.openkilda.messaging.info.event;

import org.openkilda.messaging.info.CacheTimeTag;
import org.openkilda.messaging.model.Switch;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

/**
 * Defines the payload of a Message representing a switch info.
 * <p/>
 * TODO: it doesn't look correct that we utilize SwitchInfo DTO class as cache entiries
 * and also injected cache related fields there.
 */
@Value
@ToString
@EqualsAndHashCode(callSuper = false)
public class SwitchInfoData extends CacheTimeTag {
    private static final long serialVersionUID = 1L;

    @JsonProperty("switch_id")
    private SwitchId switchId;

    @JsonProperty("address")
    private String address;

    @JsonProperty("hostname")
    private String hostname;

    @JsonProperty("description")
    private String description;

    @JsonProperty("state")
    private SwitchChangeType state;

    @JsonProperty("controller")
    private String controller;

    /**
     * This field contain all data required for ISL/network discovery. Usage of other fields of this object should
     * be avoided. Whole SwitchInfoData should not be used in new code. Old code should be update to avoid usage of
     * {@link SwitchInfoData} object.
     */
    @JsonProperty("switch")
    private Switch switchRecord;

    public SwitchInfoData(SwitchId switchId, SwitchChangeType state) {
        this(switchId, state, null, null, null, null, null);
    }

    public SwitchInfoData(SwitchId switchId, SwitchChangeType state, String address, String hostname,
                          String description, String controller) {
        this(switchId, state, address, hostname, description, controller, null);
    }

    /**
     * Instance constructor.
     *
     * @param switchId    switch datapath id
     * @param state       switch state
     * @param address     switch ip address
     * @param hostname    switch name
     * @param description switch description
     * @param controller  switch controller
     * @param switchRecord data for ISL/switch discovery
     */
    @Builder(toBuilder = true)
    @JsonCreator
    public SwitchInfoData(@JsonProperty("switch_id") SwitchId switchId,
                          @JsonProperty("state") SwitchChangeType state,
                          @JsonProperty("address") String address,
                          @JsonProperty("hostname") String hostname,
                          @JsonProperty("description") String description,
                          @JsonProperty("controller") String controller,
                          @JsonProperty("switch") Switch switchRecord) {
        this.switchId = switchId;
        this.state = state;
        this.address = address;
        this.hostname = hostname;
        this.description = description;
        this.controller = controller;
        this.switchRecord = switchRecord;
    }
}
