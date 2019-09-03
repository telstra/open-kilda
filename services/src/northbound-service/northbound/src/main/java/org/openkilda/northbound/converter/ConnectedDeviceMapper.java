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

import org.openkilda.northbound.dto.v1.flows.ConnectedDeviceDto;
import org.openkilda.northbound.dto.v1.flows.FlowConnectedDevicesResponse;
import org.openkilda.northbound.dto.v1.flows.TypedConnectedDevicesDto;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ConnectedDeviceMapper {

    ConnectedDeviceDto map(org.openkilda.messaging.nbtopology.response.ConnectedDeviceDto deviceDto);

    org.openkilda.messaging.nbtopology.response.ConnectedDeviceDto map(ConnectedDeviceDto deviceDto);

    TypedConnectedDevicesDto map(org.openkilda.messaging.nbtopology.response.TypedConnectedDevicesDto devicesDto);

    org.openkilda.messaging.nbtopology.response.TypedConnectedDevicesDto map(TypedConnectedDevicesDto devicesDto);

    FlowConnectedDevicesResponse toResponse(
            org.openkilda.messaging.nbtopology.response.FlowConnectedDevicesResponse response);

    org.openkilda.messaging.nbtopology.response.FlowConnectedDevicesResponse toMessaging(
            FlowConnectedDevicesResponse response);
}
