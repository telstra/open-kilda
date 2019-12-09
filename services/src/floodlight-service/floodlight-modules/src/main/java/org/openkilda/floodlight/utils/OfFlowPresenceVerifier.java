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

import org.openkilda.model.SwitchFeature;

import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionWriteActions;
import org.projectfloodlight.openflow.protocol.oxm.OFOxm;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmVlanVid;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmVlanVidMasked;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
public class OfFlowPresenceVerifier {
    private final DatapathId swId;
    private final Set<SwitchFeature> switchFeatures;
    private final Map<FlowLookupKey, List<OFFlowMod>> expectedOfFlows = new HashMap<>();

    @Getter
    private final CompletableFuture<OfFlowPresenceVerifier> finish = new CompletableFuture<>();

    public OfFlowPresenceVerifier(
            IOfFlowDumpProducer dumpProducer, List<OFFlowMod> expectedFlows, Set<SwitchFeature> switchFeatures) {
        swId = dumpProducer.getSwId();
        this.switchFeatures = switchFeatures;

        for (OFFlowMod entry : expectedFlows) {
            FlowLookupKey key = new FlowLookupKey(entry.getTableId(), entry.getCookie());
            log.debug("Schedule expect flow - {} on {} - {}", key, swId, entry);
            expectedOfFlows.computeIfAbsent(key, ignore -> new ArrayList<>())
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
        return expectedOfFlows.values().stream()
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
        } else {
            finish.complete(this);
        }
    }

    private void verifyTableEntry(OFFlowStatsEntry tableEntry) {
        FlowLookupKey key = new FlowLookupKey(tableEntry.getTableId(), tableEntry.getCookie());
        List<OFFlowMod> expectedChunk = expectedOfFlows.get(key);
        if (expectedChunk != null) {
            Iterator<OFFlowMod> iter = expectedChunk.iterator();
            while (iter.hasNext()) {
                OFFlowMod expectedEntry = iter.next();
                if (isEquals(expectedEntry, tableEntry)) {
                    log.debug("OF flow presence verifier on {} have found match {}", swId, tableEntry);
                    iter.remove();
                    break;
                } else {
                    log.debug("On {} mismatch {} vs expected {}", swId, tableEntry, expectedEntry);
                }
            }
        } else {
            log.debug("Skip not expected flow on {} - {}", swId, tableEntry);
        }
    }

    private boolean isEquals(OFFlowMod expectEntry, OFFlowStatsEntry tableEntry) {
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

    private boolean isInstructionsEquals(List<OFInstruction> expectedSeq, List<OFInstruction> actualSeq) {
        if (expectedSeq.size() != actualSeq.size()) {
            return false;
        }

        Map<Class<?>, OFInstruction> expectedMap = sequenceToMapByTypes(expectedSeq);
        Map<Class<?>, OFInstruction> actualMap = sequenceToMapByTypes(actualSeq);

        boolean result = true;
        for (Map.Entry<Class<?>, OFInstruction> entry : expectedMap.entrySet()) {
            OFInstruction expected = entry.getValue();
            OFInstruction actual = actualMap.get(entry.getKey());

            if (actual == null) {
                result = false;
            } else if (expected instanceof OFInstructionApplyActions) {
                result = isApplyActionsInstructionEquals(
                        (OFInstructionApplyActions) expected, (OFInstructionApplyActions) actual);
            } else if (expected instanceof OFInstructionWriteActions) {
                result = isWriteActionsInstructionEquals(
                        (OFInstructionWriteActions) expected, (OFInstructionWriteActions) actual);
            } else if (! Objects.equals(expected, actual)) {
                result = false;
            }

            if (! result) {
                break;
            }
        }
        return result;
    }

    private boolean isApplyActionsInstructionEquals(
            OFInstructionApplyActions expectedInstruction, OFInstructionApplyActions actualInstruction) {
        return isActionsListEquals(expectedInstruction.getActions(), actualInstruction.getActions());
    }

    private boolean isWriteActionsInstructionEquals(
            OFInstructionWriteActions expectedInstruction, OFInstructionWriteActions actualInstruction) {
        return isActionsListEquals(expectedInstruction.getActions(), actualInstruction.getActions());
    }

    private boolean isActionsListEquals(List<OFAction> expectedSeq, List<OFAction> actualSeq) {
        if (expectedSeq.size() != actualSeq.size()) {
            return false;
        }

        Iterator<OFAction> expectedIter = expectedSeq.iterator();
        Iterator<OFAction> actualIter = actualSeq.iterator();
        while (expectedIter.hasNext() && actualIter.hasNext()) {
            OFAction expected = expectedIter.next();
            OFAction actual = actualIter.next();

            if (! isActionEquals(expected, actual)) {
                return false;
            }
        }

        return true;
    }

    private boolean isActionEquals(OFAction expected, OFAction actual) {
        if (expected.getType() != actual.getType()) {
            return false;
        } else if (expected instanceof OFActionSetField && actual instanceof OFActionSetField) {
            return isSetFieldActionEquals((OFActionSetField) expected, (OFActionSetField) actual);
        } else {
            return Objects.equals(expected, actual);
        }
    }

    private boolean isSetFieldActionEquals(OFActionSetField expected, OFActionSetField actual) {
        if (! switchFeatures.contains(SwitchFeature.INACCURATE_SET_VLAN_VID_ACTION)) {
            return Objects.equals(expected, actual);
        }

        final OFOxm<?> expectedField = expected.getField();
        final OFOxm<?> actualField = actual.getField();
        if (expectedField instanceof OFOxmVlanVid && actualField instanceof OFOxmVlanVid) {
            return isSetVlanVidActionEquals((OFOxmVlanVid) expectedField, (OFOxmVlanVid) actualField);
        } else if (expectedField instanceof OFOxmVlanVidMasked && actualField instanceof OFOxmVlanVidMasked) {
            return isSetVlanVidMaskedActionEquals(
                    (OFOxmVlanVidMasked) expectedField, (OFOxmVlanVidMasked) actualField);
        } else {
            return Objects.equals(expected, actual);
        }
    }

    private boolean isSetVlanVidActionEquals(OFOxmVlanVid expected, OFOxmVlanVid actual) {
        return expected.getValue().getVlan() == actual.getValue().getVlan();
    }

    private boolean isSetVlanVidMaskedActionEquals(OFOxmVlanVidMasked expected, OFOxmVlanVidMasked actual) {
        return expected.getValue().getVlan() == actual.getValue().getVlan()
                && expected.getMask().getVlan() == actual.getMask().getVlan();
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
