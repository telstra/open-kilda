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

package org.openkilda.grpc.speaker.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PacketInOutStatsResponse {

    private long packetInTotalPackets;
    private long packetInTotalPacketsDataplane;
    private long packetInNoMatchPackets;
    private long packetInApplyActionPackets;
    private long packetInInvalidTtlPackets;
    private long packetInActionSetPackets;
    private long packetInGroupPackets;
    private long packetInPacketOutPackets;
    private long packetOutTotalPacketsDataplane;
    private long packetOutTotalPacketsHost;
    private Boolean packetOutEth0InterfaceUp;
    private int replyStatus;
}
