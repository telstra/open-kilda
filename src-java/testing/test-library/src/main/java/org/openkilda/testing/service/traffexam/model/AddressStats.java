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
    private Long rxPackets;
    private Long txPackets;
    private Long rxBytes;
    private Long txBytes;
    private Long rxErrors;
    private Long txErrors;
    private Long rxDropped;
    private Long txDropped;
    private Long multicast;
    private Long collisions;
    private Long rxLengthErrors;
    private Long rxOverErrors;
    private Long rxCrcErrors;
    private Long rxFrameErrors;
    private Long rxFifoErrors;
    private Long rxMissedErrors;
    private Long txAbortedErrors;
    private Long txCarrierErrors;
    private Long txFifoErrors;
    private Long txHeartbeatErrors;
    private Long txWindowErrors;
    private Long rxCompressed;
    private Long txCompressed;
}
