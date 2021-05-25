/* Copyright 2021 Telstra Open Source
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

package org.openkilda.testing.service.traffexam.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonNaming(value = SnakeCaseStrategy.class)
public class AddressStats {
    private Integer rxPackets;
    private Integer txPackets;
    private Integer rxBytes;
    private Integer txBytes;
    private Integer rxErrors;
    private Integer txErrors;
    private Integer rxDropped;
    private Integer txDropped;
    private Integer multicast;
    private Integer collisions;
    private Integer rxLengthErrors;
    private Integer rxOverErrors;
    private Integer rxCrcErrors;
    private Integer rxFrameErrors;
    private Integer rxFifoErrors;
    private Integer rxMissedErrors;
    private Integer txAbortedErrors;
    private Integer txCarrierErrors;
    private Integer txFifoErrors;
    private Integer txHeartbeatErrors;
    private Integer txWindowErrors;
    private Integer rxCompressed;
    private Integer txCompressed;
}
