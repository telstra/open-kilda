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

package org.openkilda.floodlight.utils;

import org.openkilda.messaging.MessageContext;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsRequest;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Getter
public class OfFlowDumpProducer {
    private final List<CompletableFuture<List<OFFlowStatsEntry>>> tableRequests;
    private final CompletableFuture<Void> finish;

    private final DatapathId swId;

    public OfFlowDumpProducer(MessageContext context, IOFSwitch sw, List<OFFlowMod> expectedFlows) {
        swId = sw.getId();

        HashMap<TableId, DumpSelector> targetTables = new HashMap<>();
        for (OFFlowMod entry : expectedFlows) {
            TableId tableId = entry.getTableId();  // can be null
            targetTables.computeIfAbsent(tableId, DumpSelector::new)
                    .updateCookie(entry.getCookie());
        }

        OFFactory of = sw.getOFFactory();
        tableRequests = new ArrayList<>();
        for (DumpSelector entry : targetTables.values()) {
            OFFlowStatsRequest request = makeOfFlowStatsRequest(of, entry);

            log.debug("Send flows stats request to {} - {}", sw.getId(), request);
            tableRequests.add(new CompletableFutureAdapter<>(context, sw.writeStatsRequest(request))
                                      .thenApply(stats -> unpackResponse(entry, stats)));
        }

        finish = CompletableFuture.allOf(tableRequests.toArray(new CompletableFuture<?>[0]));
    }

    private List<OFFlowStatsEntry> unpackResponse(DumpSelector selector, List<OFFlowStatsReply> ofFlowStatsReplies) {
        List<OFFlowStatsEntry> entries = ofFlowStatsReplies.stream()
                .flatMap(entry -> entry.getEntries().stream())
                .collect(Collectors.toList());
        log.debug("Receive {} entries for {} on sw:{}", entries.size(), selector, swId);
        return entries;
    }

    private static OFFlowStatsRequest makeOfFlowStatsRequest(OFFactory of, DumpSelector selector) {
        OFFlowStatsRequest.Builder request = of.buildFlowStatsRequest()
                .setOutGroup(OFGroup.ANY);
        if (selector.getCookie() != null) {
            request = request.setCookie(selector.getCookie())
                    .setCookieMask(U64.NO_MASK);
        }
        if (selector.getTableId() != null) {
            request = request.setTableId(selector.getTableId());
        }

        return request.build();
    }

    @Getter
    @ToString
    static class DumpSelector {
        private final TableId tableId;
        private U64 cookie = null;

        DumpSelector(TableId tableId) {
            this.tableId = tableId != null ? tableId : TableId.ZERO;
        }

        private void updateCookie(U64 update) {
            if (update == null) {
                return;
            }

            if (cookie == null) {
                cookie = update;
            }
        }
    }
}
