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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import java.io.Serializable;

@JsonTypeInfo(use = Id.NAME, property = "clazz")
@JsonSubTypes({
        @Type(value = FlowCommand.class,
                name = "org.openkilda.floodlight.api.request.rulemanager.FlowCommand"),
        @Type(value = MeterCommand.class,
                name = "org.openkilda.floodlight.api.request.rulemanager.MeterCommand"),
        @Type(value = GroupCommand.class,
                name = "org.openkilda.floodlight.api.request.rulemanager.GroupCommand")
})
public abstract class OfCommand implements Serializable {

    public abstract void buildInstall(OfEntityBatch builder, SwitchId switchId);

    public abstract void buildDelete(OfEntityBatch builder, SwitchId switchId);
}
