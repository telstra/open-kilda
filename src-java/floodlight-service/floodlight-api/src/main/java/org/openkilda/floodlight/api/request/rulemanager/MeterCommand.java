/* Copyright 2021 Telstra Open Source
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

package org.openkilda.floodlight.api.request.rulemanager;

import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.MeterSpeakerData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.UUID;

@Value
@EqualsAndHashCode(callSuper = false)
public class MeterCommand extends OfCommand {

    MeterSpeakerData data;

    @JsonCreator
    public MeterCommand(@JsonProperty("data") MeterSpeakerData data) {
        this.data = data;
    }

    @JsonIgnore
    @Override
    public UUID getUuid() {
        return data.getUuid();
    }

    @Override
    public void buildInstall(OfEntityBatch builder, SwitchId switchId) {
        builder.addInstallMeter(data, switchId);
    }

    @Override
    public void buildModify(OfEntityBatch builder, SwitchId switchId) {
        builder.addModifyMeter(data, switchId);
    }

    @Override
    public void buildDelete(OfEntityBatch builder, SwitchId switchId) {
        builder.addDeleteMeter(data, switchId);
    }
}
