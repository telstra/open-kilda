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

package org.openkilda.messaging.payload.history;

import org.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.time.Instant;
import java.util.List;


@Value
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonNaming(SnakeCaseStrategy.class)
public class HaFlowHistoryEntry extends InfoData {
    String haFlowId;
    Instant time;
    String timestampIso;
    String actor;
    String action;
    String taskId;
    String details;
    List<HaFlowHistoryPayload> payloads;
    List<HaFlowDumpPayload> dumps;
}
