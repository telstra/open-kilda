package org.openkilda.northbound.dto.v2.haflows;

import java.time.Instant;

public class HaFlowDumpPayload {
    Instant timestamp;
    String timestampIso;
    String action;
    String details;
}
