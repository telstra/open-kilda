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
import org.openkilda.floodlight.error.OfBatchWriteException;
import org.openkilda.floodlight.model.OfBatchResult;
import org.openkilda.floodlight.model.OfRequestResponse;
import org.openkilda.floodlight.switchmanager.OFInstallException;

import net.floodlightcontroller.core.IOFSwitch;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

class OfBatch {
    private static final Logger log = LoggerFactory.getLogger(OfBatch.class);

    private final SwitchUtils switchUtils;

    private final CompletableFuture<OfBatchResult> future = new CompletableFuture<>();

    private final HashMap<PendingKey, OfRequestResponse> pending;
    private final HashMap<PendingKey, OfRequestResponse> pendingBarrier;
    private final HashSet<DatapathId> affectedSwitches;

    private final List<OfRequestResponse> batch;
    private boolean writeCalled = false;
    private boolean error = false;
    private boolean completed;

    OfBatch(SwitchUtils switchUtils, List<OfRequestResponse> batch) {
        this.switchUtils = switchUtils;
        this.batch = batch;

        affectedSwitches = new HashSet<>();
        pending = makePendingMap(batch, affectedSwitches);

        List<OfRequestResponse> barriersBatch = makeBarriers(switchUtils, affectedSwitches);
        pendingBarrier = makePendingMap(barriersBatch, null);

        completed = pendingBarrier.size() == 0;
    }

    synchronized void write() {
        if (writeCalled) {
            throw new IllegalStateException(String.format("%s.write() can be called only once", getClass().getName()));
        }

        writeCalled = true;
        HashMap<DatapathId, IOFSwitch> switchCache = new HashMap<>();
        try {
            int count = writeBatch(batch, switchCache);
            int extraCount = writeBatch(pendingBarrier.values(), switchCache);
            log.debug("write(): {}(+{} barrier) messages", count, extraCount);
        } catch (OFInstallException e) {
            error = true;
            completed = true;

            log.error("Can't write OFMessage(xid={}) into {}", e.getOfMessage().getXid(), e.getDpId());
            pushResults();
        }
    }

    boolean receiveResponse(DatapathId dpId, OFMessage response) {
        PendingKey key = new PendingKey(dpId, response.getXid());
        OfRequestResponse entry;
        synchronized (pendingBarrier) {
            entry = pendingBarrier.remove(key);
            if (!completed && pendingBarrier.size() == 0) {
                completed = true;
                pushResults();
            }
        }

        if (entry != null) {
            // TODO(surabujin): should we check response type (is it possible to get error response on barrier message?)
            log.debug("Have barrier response on {} ({})", dpId, response);
            return true;
        }

        entry = pending.get(key);
        if (entry != null) {
            entry.setResponse(response);

            log.debug(
                    "Have response for some of payload messages (xId: {}, type: {})",
                    response.getXid(), response.getType());
            error = OFType.ERROR == response.getType();
        }

        return entry != null;
    }

    private int writeBatch(Collection<OfRequestResponse> batch, Map<DatapathId, IOFSwitch> switchCache)
            throws OFInstallException {
        int count = 0;
        for (OfRequestResponse record : batch) {
            DatapathId dpId = record.getDpId();
            IOFSwitch sw = switchCache.computeIfAbsent(dpId, switchUtils::lookupSwitch);

            if (!sw.write(record.getRequest())) {
                throw new OFInstallException(dpId, record.getRequest());
            }
            count += 1;
        }
        return count;
    }

    private void pushResults() {
        OfBatchResult result = new OfBatchResult(batch, error);
        if (error) {
            future.completeExceptionally(new OfBatchWriteException(result));
        } else {
            future.complete(result);
        }
    }

    boolean isComplete() {
        return completed;
    }

    Set<DatapathId> getAffectedSwitches() {
        return affectedSwitches;
    }

    CompletableFuture<OfBatchResult> getFuture() {
        return future;
    }

    private static HashMap<PendingKey, OfRequestResponse> makePendingMap(
            List<OfRequestResponse> requests, Set<DatapathId> collectAffectedSwitches) {
        final HashSet<DatapathId> switches = new HashSet<>();
        final HashMap<PendingKey, OfRequestResponse> result = new HashMap<>();

        for (OfRequestResponse entry : requests) {
            PendingKey key = new PendingKey(entry.getDpId(), entry.getXid());
            result.put(key, entry);
            switches.add(entry.getDpId());
        }

        if (collectAffectedSwitches != null) {
            collectAffectedSwitches.addAll(switches);
        }

        return result;
    }

    private static List<OfRequestResponse> makeBarriers(SwitchUtils switchUtils, Set<DatapathId> switches) {
        final ArrayList<OfRequestResponse> result = new ArrayList<>();

        for (DatapathId dpId : switches) {
            IOFSwitch sw = switchUtils.lookupSwitch(dpId);
            result.add(new OfRequestResponse(dpId, sw.getOFFactory().barrierRequest()));
        }

        return result;
    }

    private static class PendingKey {
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
