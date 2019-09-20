/* Copyright 2019 Telstra Open Source
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

import org.openkilda.messaging.nbtopology.response.ConnectedDeviceDto;
import org.openkilda.model.ConnectedDevice;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public abstract class ConnectedDeviceMapper {

    public static final ConnectedDeviceMapper INSTANCE = Mappers.getMapper(ConnectedDeviceMapper.class);

    public abstract ConnectedDeviceDto map(ConnectedDevice device);

    public abstract ConnectedDevice map(ConnectedDeviceDto device);
}
