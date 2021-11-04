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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Builder;
import lombok.Value;

/**
 * Represents push vlan OpenFlow action if vlanId param is 0.
 * Represents two OpenFlow actions: push vlan and set vlan id if vlanId param isn't 0.
 */
@Value
@JsonSerialize
@Builder
public class PushVlanAction implements Action {

    short vlanId;

    @Override
    public ActionType getType() {
        return ActionType.PUSH_VLAN;
    }
}
