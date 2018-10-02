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

package org.openkilda.wfm.share.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@SuppressWarnings("squid:S1214")
@Mapper
public interface SwitchIdMapper {

    SwitchIdMapper INSTANCE = Mappers.getMapper(SwitchIdMapper.class);

    /**
     * Convert {@link org.openkilda.model.SwitchId} to {@link org.openkilda.messaging.model.SwitchId}.
     */
    default org.openkilda.messaging.model.SwitchId map(org.openkilda.model.SwitchId value) {
        if (value == null) {
            return null;
        }

        return new org.openkilda.messaging.model.SwitchId(value.toString());
    }

    /**
     * Convert {@link org.openkilda.messaging.model.SwitchId} to {@link org.openkilda.model.SwitchId}.
     */
    default org.openkilda.model.SwitchId map(org.openkilda.messaging.model.SwitchId value) {
        if (value == null) {
            return null;
        }

        return new org.openkilda.model.SwitchId(value.toString());
    }
}
