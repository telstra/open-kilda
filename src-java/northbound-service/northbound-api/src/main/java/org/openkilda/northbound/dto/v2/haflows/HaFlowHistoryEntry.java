package org.openkilda.northbound.dto.v2.haflows;

import java.time.Instant;
import java.util.List;

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
