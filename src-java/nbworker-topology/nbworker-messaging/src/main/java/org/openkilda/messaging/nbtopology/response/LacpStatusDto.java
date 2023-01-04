/* Copyright 2022 Telstra Open Source
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

import org.openkilda.model.MacAddress;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Value;

import java.io.Serializable;

@Value
@JsonNaming(value = SnakeCaseStrategy.class)
public class LacpStatusDto implements Serializable {
    private static final long serialVersionUID = 1L;
    SwitchId switchId;
    int logicalPortNumber;
    int systemPriority;
    MacAddress systemId;
    int key;
    int portPriority;
    int portNumber;
    boolean stateActive;
    boolean stateShortTimeout;
    boolean stateAggregatable;
    boolean stateSynchronised;
    boolean stateCollecting;
    boolean stateDistributing;
    boolean stateDefaulted;
    boolean stateExpired;
}
