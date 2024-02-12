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

import static org.openkilda.messaging.Utils.getNonNullEntries;
import static org.openkilda.messaging.Utils.getSize;
import static org.openkilda.messaging.Utils.joinLists;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.split.SplitIterator;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonNaming(value = SnakeCaseStrategy.class)
public class SwitchPortConnectedDevicesDto extends InfoData {
    private static final long serialVersionUID = 4967031353843649179L;

    private int portNumber;
    private List<SwitchConnectedDeviceDto> lldp;
    private List<SwitchConnectedDeviceDto> arp;

    List<SwitchPortConnectedDevicesDto> split(int firstChunkSize, int chunkSize) {
        List<SwitchPortConnectedDevicesDto> result = new ArrayList<>();
        SplitIterator<SwitchPortConnectedDevicesDto, SwitchPortConnectedDevicesDtoBuilder> iterator
                = new SplitIterator<>(firstChunkSize, chunkSize, SwitchPortConnectedDevicesDtoBuilder::build,
                        () -> SwitchPortConnectedDevicesDto.builder().portNumber(portNumber));

        if (lldp != null) {
            for (List<SwitchConnectedDeviceDto> entry : Utils.split(
                    lldp, iterator.getRemainingChunkSize(), chunkSize)) {
                iterator.getCurrentBuilder().lldp(entry);
                iterator.next(entry.size()).ifPresent(result::add);
            }
        }

        if (arp != null) {
            for (List<SwitchConnectedDeviceDto> entry : Utils.split(arp, iterator.getRemainingChunkSize(), chunkSize)) {
                iterator.getCurrentBuilder().arp(entry);
                iterator.next(entry.size()).ifPresent(result::add);
            }
        }

        if (iterator.isAddCurrent()) {
            result.add(iterator.getCurrentBuilder().build());
        }
        return result;
    }

    /**
     * Unites several entities into one.
     */
    static SwitchPortConnectedDevicesDto unite(List<SwitchPortConnectedDevicesDto> dataList) {
        if (dataList == null) {
            return null;
        }
        List<SwitchPortConnectedDevicesDto> nonNullData = getNonNullEntries(dataList);
        if (nonNullData.isEmpty()) {
            return null;
        }
        SwitchPortConnectedDevicesDtoBuilder result = SwitchPortConnectedDevicesDto.builder();
        result.portNumber(Utils.joinIntegers(nonNullData.stream().map(SwitchPortConnectedDevicesDto::getPortNumber)
                .collect(Collectors.toList())));
        result.lldp(joinLists(nonNullData.stream().map(SwitchPortConnectedDevicesDto::getLldp)));
        result.arp(joinLists(nonNullData.stream().map(SwitchPortConnectedDevicesDto::getArp)));
        return result.build();
    }

    public int size() {
        return getSize(lldp) + getSize(arp);
    }
}
