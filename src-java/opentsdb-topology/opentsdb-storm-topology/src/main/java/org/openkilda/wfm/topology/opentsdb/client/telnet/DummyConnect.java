/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.opentsdb.client.telnet;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@Slf4j
class DummyConnect implements OpenTsdbTelnetSocketApi {
    private static final int QUEUE_SIZE_LIMIT_DEFAULT = 1025 * 1024;

    private final Clock clock;

    private final Instant expireAt;

    private final int queueSizeLimit;

    @Getter
    private final ReconnectDelayProvider delayProvider;

    @Getter
    private final LinkedList<String> writeQueue;

    public DummyConnect(
            Clock clock, Duration expireDelay, ReconnectDelayProvider delayProvider, LinkedList<String> writeQueue) {
        this(clock, expireDelay, delayProvider, writeQueue, QUEUE_SIZE_LIMIT_DEFAULT);
    }

    public DummyConnect(
            Clock clock, Duration expireDelay, ReconnectDelayProvider delayProvider, LinkedList<String> writeQueue,
            int queueSizeLimit) {
        this.clock = clock;
        this.delayProvider = delayProvider;
        this.writeQueue = writeQueue;

        this.expireAt = clock.instant().plus(expireDelay);
        this.queueSizeLimit = queueSizeLimit;
    }

    @Override
    public void write(String entry) throws CommunicationFailureException {
        write(Collections.singletonList(entry));
    }

    @Override
    public void write(List<String> entries) throws CommunicationFailureException {
        queueEntries(entries);
        flush();
    }

    @Override
    public void close() {
        // nothing to do here
    }

    private void queueEntries(List<String> entries) {
        long dropCount = 0;
        while (queueSizeLimit < writeQueue.size()) {
            writeQueue.removeFirst();
            dropCount++;
        }

        for (String entry : entries) {
            if (queueSizeLimit <= writeQueue.size()) {
                writeQueue.removeFirst();
                dropCount++;
            }
            writeQueue.addLast(entry);
        }

        if (0 < dropCount) {
            log.warn("{} entries of `writeQueue` were removed due to max queue size limit enforcement", dropCount);
        }
    }

    private void flush() throws CommunicationFailureException {
        if (expireAt.isBefore(clock.instant())) {
            // force reconnect
            throw new CommunicationFailureException();
        }
    }
}
