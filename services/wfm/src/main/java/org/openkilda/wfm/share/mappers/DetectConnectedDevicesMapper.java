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

import org.openkilda.messaging.model.DetectConnectedDevicesDto;
import org.openkilda.model.DetectConnectedDevices;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public abstract class DetectConnectedDevicesMapper {

    public static final DetectConnectedDevicesMapper INSTANCE = Mappers.getMapper(DetectConnectedDevicesMapper.class);

    /**
     * Convert {@link DetectConnectedDevicesDto} to {@link DetectConnectedDevices}.
     */
    public DetectConnectedDevices map(DetectConnectedDevicesDto value) {
        if (value == null) {
            return null;
        }
        return new DetectConnectedDevices(value.isSrcLldp(), value.isSrcArp(), value.isDstLldp(), value.isDstArp());
    }

    /**
     * Convert {@link DetectConnectedDevices} to {@link DetectConnectedDevicesDto}.
     */
    public DetectConnectedDevicesDto map(DetectConnectedDevices value) {
        if (value == null) {
            return null;
        }
        return new DetectConnectedDevicesDto(value.isSrcLldp(), value.isSrcArp(), value.isDstLldp(), value.isDstArp());
    }
}
