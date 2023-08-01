/* Copyright 2023 Telstra Open Source
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class SwitchConnectedDevicesResponseTest {
    @Test
    public void splitAndUniteLoop() {
        SwitchConnectedDevicesResponse originalResponse = SwitchConnectedDevicesResponse.builder()
                .port(buildRandomPortConnectedDevice(1, 10, 20))
                .port(buildRandomPortConnectedDevice(2, 20, 0))
                .port(buildRandomPortConnectedDevice(3, 0, 7))
                .port(buildRandomPortConnectedDevice(4, 4, 11))
                .build();
        List<SwitchConnectedDevicesResponse> splittedList = originalResponse.split(10);
        assertEquals(8, splittedList.size());
        for (int i = 0; i < splittedList.size() - 1; i++) {
            assertEquals(10, getSize(splittedList.get(i)));
        }
        assertEquals(2, getSize(splittedList.get(splittedList.size() - 1)));
        assertEquals(originalResponse, SwitchConnectedDevicesResponse.unite(splittedList));
    }

    @Test
    public void emptyResponse() {
        SwitchConnectedDevicesResponse originalResponse = SwitchConnectedDevicesResponse.builder()
                .ports(new ArrayList<>())
                .build();
        List<SwitchConnectedDevicesResponse> splittedList = originalResponse.split(10);
        assertEquals(1, splittedList.size());
        assertEquals(0, splittedList.get(0).getPorts().size());
        assertEquals(originalResponse, SwitchConnectedDevicesResponse.unite(splittedList));
    }

    private int getSize(SwitchConnectedDevicesResponse response) {
        int size = 0;
        for (SwitchPortConnectedDevicesDto port : response.getPorts()) {
            size += port.size();
        }
        return size;
    }

    private SwitchPortConnectedDevicesDto buildRandomPortConnectedDevice(int portNumber, int arpSize, int lldpSize) {
        List<SwitchConnectedDeviceDto> arps = new ArrayList<>();
        List<SwitchConnectedDeviceDto> lldps = new ArrayList<>();

        for (int i = 0; i < arpSize; i++) {
            arps.add(SwitchConnectedDeviceDto.builder()
                    .portId(String.format("arp_port_%s_%s", portNumber, i))
                    .vlan(i + portNumber)
                    .build());
        }
        for (int i = 0; i < lldpSize; i++) {
            arps.add(SwitchConnectedDeviceDto.builder()
                    .portId(String.format("lldp_port_%s_%s", portNumber, i))
                    .vlan(i + portNumber)
                    .build());
        }
        return new SwitchPortConnectedDevicesDto(portNumber, lldps, arps);
    }
}
