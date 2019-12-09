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

import org.openkilda.messaging.info.InfoData;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@AllArgsConstructor
public class SwitchLldpInfoData extends InfoData {

    private static final long serialVersionUID = 7963516610743491465L;

    private SwitchId switchId;
    private int portNumber;
    private List<Integer> vlans;
    private long cookie;
    private String macAddress;
    private String chassisId;
    private String portId;
    private Integer ttl;
    private String portDescription;
    private String systemName;
    private String systemDescription;
    private String systemCapabilities;
    private String managementAddress;
}
