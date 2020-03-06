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

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.io.Serializable;

@Data
@JsonNaming(value = SnakeCaseStrategy.class)
public class ConnectedDeviceDto implements Serializable {
    private static final long serialVersionUID = 6293390020243063935L;

    private String macAddress;
    private String ipAddress;
    private String chassisId;
    private String portId;
    private Integer ttl;
    private String portDescription;
    private String systemName;
    private String systemDescription;
    private String systemCapabilities;
    private String managementAddress;
    private String timeFirstSeen;
    private String timeLastSeen;
}
