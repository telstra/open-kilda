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

package org.openkilda.floodlight.service.session;

import org.openkilda.floodlight.error.SessionCloseException;
import org.openkilda.floodlight.error.SessionConnectionLostException;
import org.openkilda.floodlight.error.SessionErrorResponseException;
import org.openkilda.floodlight.error.SessionRevertException;
import org.openkilda.floodlight.error.SwitchWriteException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFBarrierRequest;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class Session implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(Session.class);

    private final SwitchSessions group;
    private final IOFSwitch sw;

    private CompletableFuture<Optional<OFMessage>> closingBarrier;
    private boolean error = false;
    private boolean completed = false;

    private final Map<Long, CompletableFuture<Optional<OFMessage>>> requestsByXid = new HashMap<>();

    Session(SwitchSessions group, IOFSwitch sw) {
        this.group = group;
        this.sw = sw;
    }

    /**
     * Send OF message to the switch and register it in session to trace possible responses.
     */
    public CompletableFuture<Optional<OFMessage>> write(OFMessage message) throws SwitchWriteException {
        ensureOpen();

        CompletableFuture<Optional<OFMessage>> future = prepareRequest(message);
        actualWrite(future, message);

        return future;
    }

    public void resetError() {
        error = false;
    }

    @Override
    public void close() throws SwitchWriteException {
        if (error) {
            SessionRevertException e = new SessionRevertException(sw.getId());
            incompleteRequestsStream()
                    .forEach(entry -> entry.completeExceptionally(e));
            return;
        }

        if (closingBarrier != null) {
            throw new IllegalStateException("Session already closed");
        }

        OFBarrierRequest barrier = sw.getOFFactory().barrierRequest();
        closingBarrier = prepareRequest(barrier);
        try {
            actualWrite(closingBarrier, barrier);
        } catch (SwitchWriteException e) {
            SessionCloseException closeError = new SessionCloseException(sw.getId());
            incompleteRequestsStream()
                    .forEach(entry -> entry.completeExceptionally(closeError));
            throw e;
        }
    }

    void disconnect() {
        // must be safe to be called multiple times
        if (completed) {
            return;
        }
        completed = true;

        SessionConnectionLostException e = new SessionConnectionLostException(sw.getId());
        incompleteRequestsStream()
                .forEach(entry -> entry.completeExceptionally(e));
    }

    boolean handleResponse(OFMessage message) {
        CompletableFuture<Optional<OFMessage>> future;
        synchronized (this) {
            future = requestsByXid.get(message.getXid());
        }

        if (future == null) {
            throw new IllegalArgumentException(String.format(
                    "%s must never route \"foreign\" response", group.getClass().getName()));
        }
        if (future.isDone()) {
            // it can already be marked as failed by results of some session wide errors
            return false;
        }

        if (OFType.ERROR == message.getType()) {
            future.completeExceptionally(new SessionErrorResponseException(sw.getId(), (OFErrorMsg) message));
        } else {
            future.complete(Optional.of(message));
        }

        // check session completion (we have received all responses, if we got response for closing barrier request)
        if (closingBarrier.isDone()) {
            incompleteRequestsStream()
                    .forEach(entry -> entry.complete(Optional.empty()));
            return true;
        }
        return false;
    }

    Set<Long> getAllXids() {
        synchronized (requestsByXid) {
            return ImmutableSet.copyOf(requestsByXid.keySet());
        }
    }

    private CompletableFuture<Optional<OFMessage>> prepareRequest(OFMessage message) {
        CompletableFuture<Optional<OFMessage>> future = new CompletableFuture<>();

        long xid = message.getXid();
        synchronized (requestsByXid) {
            requestsByXid.put(xid, future);
        }
        group.bindRequest(this, xid);

        return future;
    }

    private void actualWrite(CompletableFuture<Optional<OFMessage>> future, OFMessage message)
            throws SwitchWriteException {
        log.debug("push OF message to {}: {}", sw.getId(), message);
        if (!sw.write(message)) {
            error = true;

            SwitchWriteException e = new SwitchWriteException(sw.getId(), message);
            future.completeExceptionally(e);
            throw e;
        }
    }

    private Stream<CompletableFuture<Optional<OFMessage>>> incompleteRequestsStream() {
        ImmutableList<CompletableFuture<Optional<OFMessage>>> requestsSafeCopy;
        synchronized (requestsByXid) {
            requestsSafeCopy = ImmutableList.copyOf(requestsByXid.values());
        }

        return requestsSafeCopy.stream()
                .filter(entry -> !entry.isDone());
    }

    private void ensureOpen() {
        if (closingBarrier != null) {
            throw new IllegalStateException("Session is closed");
        }
    }

    // getter & setters
    public IOFSwitch getSw() {
        return sw;
    }
}
