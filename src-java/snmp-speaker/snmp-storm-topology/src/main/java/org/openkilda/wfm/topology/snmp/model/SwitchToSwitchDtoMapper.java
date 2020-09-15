/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.snmp.model;

import org.openkilda.model.Switch;

import org.mapstruct.Mapper;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Mapper
public abstract class SwitchToSwitchDtoMapper {

    /**
     * Convert a {@link Switch} to {@link SwitchDto}.
     *
     * @param sw A kilda Switch object
     * @return   A SwitchDto object.
     */
    public SwitchDto switchToSwitchDto(Switch sw) {
        SwitchDto dto = new SwitchDto();
        dto.setHostname(sw.getHostname());
        Map<String, String> tags = new HashMap<>();
        tags.put("switchid", sw.getSwitchId().toOtsdFormat());
        dto.setTags(tags);
        return dto;
    }

    public abstract Collection<SwitchDto> switchesToSwitchDtos(Collection<Switch> sws);
}
