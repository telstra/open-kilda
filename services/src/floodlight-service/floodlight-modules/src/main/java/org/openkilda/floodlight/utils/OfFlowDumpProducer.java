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
import org.openkilda.model.Cookie;

import com.google.common.collect.ImmutableList;
import lombok.Getter;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsRequest;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Getter
public class OfFlowDumpProducer {
    private final List<CompletableFuture<List<OFFlowStatsEntry>>> tableRequests;
    private final CompletableFuture<Void> finish;

    public OfFlowDumpProducer(MessageContext context, IOFSwitch sw, List<OFFlowMod> expectedFlows) {
        HashMap<TableId, DumpSelector> targetTables = new HashMap<>();
        for (OFFlowMod entry : expectedFlows) {
            TableId tableId = entry.getTableId();  // can be null
            targetTables.computeIfAbsent(tableId, DumpSelector::new)
                    .updateCookie(entry.getCookie());
        }

        tableRequests = makeRequests(context, sw, targetTables.values());
        finish = CompletableFuture.allOf(tableRequests.toArray(new CompletableFuture<?>[0]));
    }

    private static List<CompletableFuture<List<OFFlowStatsEntry>>> makeRequests(
            MessageContext context, IOFSwitch sw, Collection<DumpSelector> targets) {
        OFFactory of = sw.getOFFactory();
        List<CompletableFuture<List<OFFlowStatsEntry>>> requests = new ArrayList<>();

        for (DumpSelector entry : targets) {
            OFFlowStatsRequest r = makeOfFlowStatsRequest(of, entry);
            requests.add(new CompletableFutureAdapter<>(context, sw.writeStatsRequest(r))
                                 .thenApply(OfFlowDumpProducer::unpackResponse));
        }

        return ImmutableList.copyOf(requests);
    }

    private static List<OFFlowStatsEntry> unpackResponse(List<OFFlowStatsReply> ofFlowStatsReplies) {
        return ofFlowStatsReplies.stream()
                .flatMap(entry -> entry.getEntries().stream())
                .collect(Collectors.toList());
    }

    private static OFFlowStatsRequest makeOfFlowStatsRequest(OFFactory of, DumpSelector selector) {
        U64 systemFlowMask = U64.of(Cookie.DEFAULT_RULES_MASK);

        U64 value = U64.ZERO;
        U64 mask = systemFlowMask;

        if (selector.getCookie() != null) {
            value = selector.getCookie();
            mask = mask.or(selector.cookie);
        }

        OFFlowStatsRequest.Builder request = of.buildFlowStatsRequest()
                .setCookie(value)
                .setCookieMask(mask);
        if (selector.getTableId() != null) {
            request = request.setTableId(selector.getTableId());
        }

        return request.build();
    }

    @Getter
    static class DumpSelector {
        private final TableId tableId;
        private U64 cookie = null;

        DumpSelector(TableId tableId) {
            this.tableId = tableId;
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
