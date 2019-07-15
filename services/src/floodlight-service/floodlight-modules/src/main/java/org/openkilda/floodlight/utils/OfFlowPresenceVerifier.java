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

import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionWriteActions;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
public class OfFlowPresenceVerifier {
    private final Map<FlowLookupKey, List<OFFlowMod>> expected = new HashMap<>();

    @Getter
    private final CompletableFuture<OfFlowPresenceVerifier> finish = new CompletableFuture<>();

    public OfFlowPresenceVerifier(OfFlowDumpProducer dumpProducer, List<OFFlowMod> expectedFlows) {
        for (OFFlowMod entry : expectedFlows) {
            FlowLookupKey key = new FlowLookupKey(entry.getTableId(), entry.getCookie());
            log.debug("Schedule expect flow - {} - {}", key, entry);
            expected.computeIfAbsent(key, ignore -> new ArrayList<>())
                    .add(entry);
        }

        List<CompletableFuture<Void>> pending = new ArrayList<>();
        for (CompletableFuture<List<OFFlowStatsEntry>> tableDump : dumpProducer.getTableRequests()) {
            pending.add(tableDump.thenAccept(this::handleTableFlowStats));
        }

        CompletableFuture.allOf(pending.toArray(new CompletableFuture<?>[0]))
                .whenComplete((ignore, error) -> handleComplete(error));
    }

    /**
     * Expose "missing" OF flows.
     */
    public List<OFFlowMod> getMissing() {
        return expected.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private void handleTableFlowStats(List<OFFlowStatsEntry> tableEntries) {
        for (OFFlowStatsEntry entry : tableEntries) {
            verifyTableEntry(entry);
        }
    }

    private void handleComplete(Throwable error) {
        if (error != null) {
            finish.completeExceptionally(error);
        }
        finish.complete(this);
    }

    private void verifyTableEntry(OFFlowStatsEntry tableEntry) {
        FlowLookupKey key = new FlowLookupKey(tableEntry.getTableId(), tableEntry.getCookie());
        List<OFFlowMod> expectedChunk = expected.get(key);
        if (expectedChunk != null) {
            Iterator<OFFlowMod> iter = expectedChunk.iterator();
            while (iter.hasNext()) {
                OFFlowMod expectedEntry = iter.next();
                if (isEquals(expectedEntry, tableEntry)) {
                    log.debug("Found match {}", tableEntry);
                    iter.remove();
                    break;
                } else {
                    log.debug("Mismatch {} vs {}", tableEntry, expectedEntry);
                }
            }
        } else {
            log.debug("Skip not expected flow - {}", tableEntry);
        }
    }

    private static boolean isEquals(OFFlowMod expectEntry, OFFlowStatsEntry tableEntry) {
        if (! Objects.equals(expectEntry.getPriority(), tableEntry.getPriority())) {
            return false;
        }
        if (! Objects.equals(expectEntry.getFlags(), tableEntry.getFlags())) {
            return false;
        }
        if (! Objects.equals(expectEntry.getMatch(), tableEntry.getMatch())) {
            return false;
        }
        return isInstructionsEquals(expectEntry.getInstructions(), tableEntry.getInstructions());
    }

    private static boolean isInstructionsEquals(List<OFInstruction> expectedSeq, List<OFInstruction> actualSeq) {
        if (expectedSeq.size() != actualSeq.size()) {
            return false;
        }

        Map<Class<?>, OFInstruction> expectedMap = sequenceToMapByTypes(expectedSeq);
        Map<Class<?>, OFInstruction> actualMap = sequenceToMapByTypes(actualSeq);

        for (Map.Entry<Class<?>, OFInstruction> entry : expectedMap.entrySet()) {
            OFInstruction expected = entry.getValue();
            OFInstruction actual = actualMap.get(entry.getKey());

            if (expected instanceof OFInstructionWriteActions) {
                if (! isWriteActionsInstructionEquals((OFInstructionWriteActions) expected,
                                                      (OFInstructionWriteActions) actual)) {
                    return false;
                }
            } else if (! Objects.equals(expected, actual)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWriteActionsInstructionEquals(
            OFInstructionWriteActions expectedInstruction, OFInstructionWriteActions actualInstruction) {
        List<OFAction> expectedSeq = expectedInstruction.getActions();
        List<OFAction> actualSeq = actualInstruction.getActions();
        if (expectedSeq.size() != actualSeq.size()) {
            return false;
        }

        Map<Class<?>, OFAction> expectedMap = sequenceToMapByTypes(expectedSeq);
        Map<Class<?>, OFAction> actualMap = sequenceToMapByTypes(actualSeq);

        for (Map.Entry<Class<?>, OFAction> entry : expectedMap.entrySet()) {
            OFAction expected = entry.getValue();
            OFAction actual = actualMap.get(entry.getKey());

            if (! Objects.equals(expected, actual)) {
                return false;
            }
        }

        return true;
    }

    private static <T> Map<Class<?>, T> sequenceToMapByTypes(List<T> sequence) {
        Map<Class<?>, T> map = new HashMap<>();
        for (T entry : sequence) {
            map.put(entry.getClass(), entry);
        }
        return map;
    }

    @Value
    private static class FlowLookupKey {
        TableId tableId;
        U64 cookie;

        FlowLookupKey(TableId tableId, U64 cookie) {
            this.tableId = tableId != null ? tableId : TableId.of(0);
            this.cookie = cookie != null ? cookie : U64.ZERO;
        }
    }
}
