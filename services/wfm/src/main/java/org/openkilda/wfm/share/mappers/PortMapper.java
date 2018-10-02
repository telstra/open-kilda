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

import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.model.Port;
import org.openkilda.model.PortStatus;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@SuppressWarnings("squid:S1214")
@Mapper(uses = SwitchIdMapper.class)
public interface PortMapper {

    PortMapper INSTANCE = Mappers.getMapper(PortMapper.class);

    @Mapping(source = "state", target = "status")
    Port map(PortInfoData portInfoData);

    PortInfoData map(Port port);

    /**
     * Transforms {@link PortChangeType} to DAO model object.
     *
     * @param portChangeType port change type.
     * @return converted port status.
     */
    default PortStatus map(PortChangeType portChangeType) {
        switch (portChangeType) {
            case ADD:
            case UP:
                return PortStatus.UP;
            case DELETE:
            case DOWN:
                return PortStatus.DOWN;
            default:
                return PortStatus.DOWN;
        }
    }
}
