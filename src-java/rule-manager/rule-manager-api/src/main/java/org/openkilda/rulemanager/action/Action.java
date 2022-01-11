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

package org.openkilda.rulemanager.action;

import org.openkilda.rulemanager.action.noviflow.CopyFieldAction;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NAME, property = "clazz")
@JsonSubTypes({
        @Type(value = CopyFieldAction.class,
                name = "org.openkilda.rulemanager.action.noviflow.CopyFieldAction"),
        @Type(value = GroupAction.class,
                name = "org.openkilda.rulemanager.action.GroupAction"),
        @Type(value = MeterAction.class,
                name = "org.openkilda.rulemanager.action.MeterAction"),
        @Type(value = PopVlanAction.class,
                name = "org.openkilda.rulemanager.action.PopVlanAction"),
        @Type(value = PopVxlanAction.class,
                name = "org.openkilda.rulemanager.action.PopVxlanAction"),
        @Type(value = PortOutAction.class,
                name = "org.openkilda.rulemanager.action.PortOutAction"),
        @Type(value = PushVlanAction.class,
                name = "org.openkilda.rulemanager.action.PushVlanAction"),
        @Type(value = PushVxlanAction.class,
                name = "org.openkilda.rulemanager.action.PushVxlanAction"),
        @Type(value = SetFieldAction.class,
                name = "org.openkilda.rulemanager.action.SetFieldAction"),
        @Type(value = SwapFieldAction.class,
                name = "org.openkilda.rulemanager.action.SwapFieldAction"),
})
public interface Action {

    ActionType getType();
}
