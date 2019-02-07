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

package org.openkilda.northbound.converter;

import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.switches.SyncRulesResponse;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.dto.switches.RulesSyncResult;
import org.openkilda.northbound.dto.switches.RulesValidationResult;
import org.openkilda.northbound.dto.switches.SwitchDto;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SwitchMapper {

    SwitchDto toSwitchDto(SwitchInfoData data);

    RulesSyncResult toRulesSyncResult(SyncRulesResponse response);

    RulesValidationResult toRulesValidationResult(SyncRulesResponse response);

    default String toSwithId(SwitchId switchId) {
        return switchId.toString();
    }
}
