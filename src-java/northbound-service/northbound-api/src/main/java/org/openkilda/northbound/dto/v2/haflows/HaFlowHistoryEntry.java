package org.openkilda.northbound.dto.v2.haflows;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
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
public class HaFlowHistoryEntry {
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