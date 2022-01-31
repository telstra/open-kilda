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

package org.openkilda.wfm.share.utils;

import org.openkilda.wfm.share.flow.resources.ResourceNotAvailableException;

import com.google.common.base.Preconditions;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.Random;

@Slf4j
public class PoolManager<T> {
    protected final PoolConfig config;
    private final PoolEntityAdapter<T> entityAdapter;

    private final Random random = new Random();
    private long lastId;

    public PoolManager(PoolConfig config, PoolEntityAdapter<T> entityAdapter) {
        this.config = config;
        this.entityAdapter = entityAdapter;

        this.lastId = config.idMaximum;
    }

    /**
     * Allocate one entity.
     */
    public T allocate() {
        Optional<T> entity = allocateNext();
        if (! entity.isPresent()) {
            entity = allocateInChunk();
        }
        if (! entity.isPresent()) {
            entity = allocateInFullScan();
        }
        if (! entity.isPresent()) {
            throw new ResourceNotAvailableException(entityAdapter.formatResourceNotAvailableMessage());
        }

        T value = entity.get();
        lastId = entityAdapter.getNumericSequentialId(value);
        log.trace("Pool entity have been successfully allocated id=={}", lastId);
        return value;
    }

    private Optional<T> allocateNext() {
        long nextId = lastId + 1;
        if (config.idMaximum <= nextId) {
            return Optional.empty();
        }
        log.trace("Attempt to allocate pool entity by id == {}", nextId);
        return entityAdapter.allocateSpecificId(nextId);
    }

    private Optional<T> allocateInChunk() {
        long chunkNumber = selectChunkNumber(config.chunksCount);
        long chunkSize = (config.idMaximum - config.idMinimum) / config.chunksCount;
        long first = config.idMinimum + chunkNumber * chunkSize;

        long last;
        if (chunkNumber + 1 < config.chunksCount) {
            last = Math.min(first + chunkSize - 1, config.idMaximum);
        } else {
            last = config.idMaximum;
        }

        log.trace("Attempt to allocate pool entity in chunk from {} till {}", first, last);
        return entityAdapter.allocateFirstInRange(first, last);
    }

    private Optional<T> allocateInFullScan() {
        log.trace(
                "Attempt to allocate pool entity using full scan (idMin=={}, idMax=={})",
                config.idMinimum, config.idMaximum);
        return entityAdapter.allocateFirstInRange(config.idMinimum, config.idMaximum);
    }

    protected long selectChunkNumber(long chunksCount) {
        if (chunksCount <= 1) {
            return 0;
        }
        return Math.abs(random.nextInt() % chunksCount);
    }

    @Value
    public static class PoolConfig {
        long idMinimum;
        long idMaximum;
        long chunksCount;

        public PoolConfig(long idMinimum, long idMaximum, long chunksCount) {
            long size = idMaximum - idMinimum;
            Preconditions.checkArgument(
                    0 < size, String.format(
                            "Resources pool must have at least one entry (%d(idMaximum) - %d(idMinimum) == %d)",
                            idMaximum, idMinimum, size));
            Preconditions.checkArgument(
                    0 < chunksCount && chunksCount <= size, String.format(
                            "Invalid pool chunks count, the expression must be correct: 0 < %d <= %d",
                            chunksCount, size));

            this.idMinimum = idMinimum;
            this.idMaximum = idMaximum;
            this.chunksCount = chunksCount;
        }
    }
}
