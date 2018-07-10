/* Copyright 2018 Telstra Open Source
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

package org.openkilda.floodlight.service.batch;

import org.openkilda.floodlight.SwitchUtils;
import org.openkilda.floodlight.error.OfBatchException;
import org.openkilda.floodlight.error.OfLostConnectionException;
import org.openkilda.floodlight.error.OfWriteException;
import org.openkilda.floodlight.model.OfRequestResponse;

import com.google.common.collect.ImmutableList;
import net.floodlightcontroller.core.IOFSwitch;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.projectfloodlight.openflow.protocol.OFBarrierRequest;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

class OfBatch {
    private static final Logger log = LoggerFactory.getLogger(OfBatch.class);

    private final SwitchUtils switchUtils;

    private final CompletableFuture<List<OfRequestResponse>> future = new CompletableFuture<>();

    private final HashMap<PendingKey, OfRequestResponse> pending = new HashMap<>();
    private final HashSet<PendingKey> pendingBarriers = new HashSet<>();
    private final HashSet<DatapathId> affectedSwitches = new HashSet<>();

    private final List<OfRequestResponse> payload = new ArrayList<>();
    private final ArrayList<OfRequestResponse> barrier = new ArrayList<>();
    private boolean writeIsOver = false;
    private boolean completed;

    OfBatch(SwitchUtils switchUtils, List<OfRequestResponse> requests) {
        this.switchUtils = switchUtils;
        this.payload.addAll(requests);

        fillPending(requests);
        fillPendingBarriers();

        if (pendingBarriers.size() == 0) {
            pushResults();
        }
    }

    void write() {
        synchronized (this) {
            if (writeIsOver) {
                throw new IllegalStateException(String.format(
                        "%s.write() can be called only once", getClass().getName()));
            }
            writeIsOver = true;
        }

        try {
            HashMap<DatapathId, IOFSwitch> switchCache = new HashMap<>();
            List<Long> processed = writeToSwitches(payload, switchCache);
            processed.addAll(writeToSwitches(barrier, switchCache));
            log.debug("write xId(s): {} messages", formatXidSequence(processed));
        } catch (OfWriteException e) {
            log.error(e.getMessage());
            pushResults();
        }
    }

    boolean receiveResponse(DatapathId dpId, OFMessage response) {
        PendingKey key = new PendingKey(dpId, response.getXid());
        OfRequestResponse entry = pending.get(key);
        if (entry == null) {
            return false;
        }

        entry.setResponse(response);
        log.debug("Got OF response - {}.{}:{}", response.getType(), response.getVersion(), response.getXid());

        Integer stillPending = null;
        synchronized (pendingBarriers) {
            if (pendingBarriers.remove(key)) {
                stillPending = pendingBarriers.size();
            }
        }

        if (stillPending != null) {
            int total = affectedSwitches.size();
            log.debug("Got {} of {} barrier response (sw: {})", total - stillPending, total, dpId);

            if (stillPending == 0) {
                pushResults();
            }
        }

        return true;
    }

    void lostConnection(DatapathId dpId) {
        synchronized (pendingBarriers) {
            for (Iterator<PendingKey> iterator = pendingBarriers.iterator(); iterator.hasNext(); ) {
                PendingKey key = iterator.next();
                if (! key.dpId.equals(dpId)) {
                    continue;
                }
                iterator.remove();
                break;
            }
        }

        OfLostConnectionException error = new OfLostConnectionException(dpId);
        for (OfRequestResponse entry : pending.values()) {
            if (dpId.equals(entry.getDpId())) {
                entry.setError(error);
            }
        }
    }

    private List<Long> writeToSwitches(List<OfRequestResponse> requests, HashMap<DatapathId, IOFSwitch> switchCache)
            throws OfWriteException {
        ArrayList<Long> processedXid = new ArrayList<>();
        for (OfRequestResponse entry : requests) {
            DatapathId dpId = entry.getDpId();
            IOFSwitch sw = switchCache.computeIfAbsent(dpId, switchUtils::lookupSwitch);

            final OFMessage request = entry.getRequest();
            if (!sw.write(request)) {
                OfWriteException error = new OfWriteException(dpId, request);
                entry.setError(error);
                throw error;
            }
            processedXid.add(request.getXid());
        }
        return processedXid;
    }

    private void pushResults() {
        synchronized (future) {
            if (completed) {
                return;
            }
            completed = true;
        }

        List<OfRequestResponse> errors = payload.stream()
                .filter(entry -> entry.getError() != null)
                .collect(Collectors.toList());
        if (errors.size() == 0) {
            future.complete(payload);
        } else {
            future.completeExceptionally(new OfBatchException(errors));
        }
    }

    boolean isGarbage() {
        return completed || future.isCancelled();
    }

    Set<DatapathId> getAffectedSwitches() {
        return affectedSwitches;
    }

    CompletableFuture<List<OfRequestResponse>> getFuture() {
        return future;
    }

    List<PendingKey> getPendingBarriers() {
        synchronized (pendingBarriers) {
            return ImmutableList.copyOf(pendingBarriers);
        }
    }

    private void fillPending(List<OfRequestResponse> requests) {
        for (OfRequestResponse entry : requests) {
            PendingKey key = new PendingKey(entry.getDpId(), entry.getXid());
            pending.put(key, entry);
            affectedSwitches.add(entry.getDpId());
        }
    }

    private void fillPendingBarriers() {
        for (DatapathId dpId : affectedSwitches) {
            IOFSwitch sw = switchUtils.lookupSwitch(dpId);
            OFBarrierRequest request = sw.getOFFactory().barrierRequest();
            PendingKey pendingKey = new PendingKey(dpId, request.getXid());

            final OfRequestResponse requestResponse = new OfRequestResponse(dpId, request);
            pending.put(pendingKey, requestResponse);
            pendingBarriers.add(pendingKey);
            barrier.add(requestResponse);
        }
    }

    private static String formatXidSequence(List<Long> sequence) {
        return sequence.stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "));
    }

    static class PendingKey {
        DatapathId dpId;
        long xid;

        PendingKey(DatapathId dpId, long xid) {
            this.dpId = dpId;
            this.xid = xid;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            PendingKey that = (PendingKey) o;
            return new EqualsBuilder()
                    .append(xid, that.xid)
                    .append(dpId, that.dpId)
                    .isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(17, 37)
                    .append(dpId)
                    .append(xid)
                    .toHashCode();
        }
    }
}
