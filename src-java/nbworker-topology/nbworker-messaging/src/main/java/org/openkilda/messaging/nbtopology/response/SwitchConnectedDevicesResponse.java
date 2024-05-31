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

package org.openkilda.messaging.nbtopology.response;

import org.openkilda.messaging.Chunkable;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.split.SplitIterator;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Singular;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonNaming(value = SnakeCaseStrategy.class)
public class SwitchConnectedDevicesResponse extends InfoData implements Chunkable<SwitchConnectedDevicesResponse> {
    @Singular
    private List<SwitchPortConnectedDevicesDto> ports;

    @Override
    public List<SwitchConnectedDevicesResponse> split(int chunkSize) {
        List<SwitchConnectedDevicesResponse> result = new ArrayList<>();
        SplitIterator<SwitchConnectedDevicesResponse, SwitchConnectedDevicesResponseBuilder> iterator
                = new SplitIterator<>(chunkSize, chunkSize, SwitchConnectedDevicesResponseBuilder::build,
                SwitchConnectedDevicesResponse::builder);

        if (ports != null) {
            for (SwitchPortConnectedDevicesDto port : ports) {
                for (SwitchPortConnectedDevicesDto devicesDto : port.split(
                        iterator.getRemainingChunkSize(), chunkSize)) {
                    iterator.getCurrentBuilder().port(devicesDto);
                    iterator.next(devicesDto.size()).ifPresent(result::add);
                }
            }
        }
        if (iterator.isAddCurrent()) {
            result.add(iterator.getCurrentBuilder().build());
        }

        return result;
    }

    /**
     * Unites several responses into one.
     */
    public static SwitchConnectedDevicesResponse unite(List<SwitchConnectedDevicesResponse> dataList) {
        if (dataList == null) {
            return null;
        }
        Map<Integer, List<SwitchPortConnectedDevicesDto>> devicesByPortOrderedMap = new LinkedHashMap<>();

        for (SwitchConnectedDevicesResponse response : dataList) {
            if (response != null && response.getPorts() != null) {
                for (SwitchPortConnectedDevicesDto devicesDto : response.ports) {
                    devicesByPortOrderedMap.putIfAbsent(devicesDto.getPortNumber(), new ArrayList<>());
                    devicesByPortOrderedMap.get(devicesDto.getPortNumber()).add(devicesDto);
                }
            }
        }

        List<SwitchPortConnectedDevicesDto> connectedDevicesDtos = new ArrayList<>();
        for (Integer portNumber : devicesByPortOrderedMap.keySet()) {
            connectedDevicesDtos.add(SwitchPortConnectedDevicesDto.unite(devicesByPortOrderedMap.get(portNumber)));
        }
        return new SwitchConnectedDevicesResponse(connectedDevicesDtos);
    }
}
