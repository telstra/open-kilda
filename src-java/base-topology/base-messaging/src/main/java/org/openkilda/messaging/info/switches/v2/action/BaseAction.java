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

package org.openkilda.messaging.info.switches.v2.action;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import java.io.Serializable;

@JsonTypeInfo(use = Id.NAME, property = "clazz")
@JsonSubTypes({
        @Type(value = CopyFieldActionEntry.class,
                name = "org.openkilda.messaging.info.switches.v2.action.noviflow.CopyFieldAction"),
        @Type(value = GroupActionEntry.class,
                name = "org.openkilda.messaging.info.switches.v2.action.GroupActionEntry"),
        @Type(value = MeterActionEntry.class,
                name = "org.openkilda.messaging.info.switches.v2.action.MeterAction"),
        @Type(value = PopVlanActionEntry.class,
                name = "org.openkilda.messaging.info.switches.v2.action.PopVlanAction"),
        @Type(value = PopVxlanActionEntry.class,
                name = "org.openkilda.messaging.info.switches.v2.action.PopVxlanAction"),
        @Type(value = PortOutActionEntry.class,
                name = "org.openkilda.messaging.info.switches.v2.action.PortOutAction"),
        @Type(value = PushVlanActionEntry.class,
                name = "org.openkilda.messaging.info.switches.v2.action.PushVlanAction"),
        @Type(value = PushVxlanActionEntry.class,
                name = "org.openkilda.messaging.info.switches.v2.action.PushVxlanAction"),
        @Type(value = SetFieldActionEntry.class,
                name = "org.openkilda.messaging.info.switches.v2.action.SetFieldAction"),
        @Type(value = SwapFieldActionEntry.class,
                name = "org.openkilda.messaging.info.switches.v2.action.SwapFieldAction"),
})
public interface BaseAction extends Serializable {
    String getActionType();
}
