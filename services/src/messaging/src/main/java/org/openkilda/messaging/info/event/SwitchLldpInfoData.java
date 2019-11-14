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

package org.openkilda.messaging.info.event;

import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@AllArgsConstructor
public class SwitchLldpInfoData extends LldpInfoData {

    private static final long serialVersionUID = 7963516610743491465L;

    private SwitchId switchId;
    private int portNumber;
    private int vlan;

    public SwitchLldpInfoData(SwitchId switchId, int portNumber, int vlan, long cookie, String macAddress,
                              String chassisId, String portId, Integer ttl, String portDescription, String systemName,
                              String systemDescription, String systemCapabilities, String managementAddress) {
        super(cookie, macAddress, chassisId, portId, ttl, portDescription, systemName, systemDescription,
                systemCapabilities, managementAddress);
        this.switchId = switchId;
        this.portNumber = portNumber;
        this.vlan = vlan;
    }
}
