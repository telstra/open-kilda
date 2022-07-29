/* Copyright 2022 Telstra Open Source
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
import org.openkilda.wfm.share.utils.PoolManager.PoolConfig;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PoolManagerTest {
    @Test
    public void testSequentialAllocation() {
        InMemorySetPoolEntityAdapter adapter = new InMemorySetPoolEntityAdapter();

        PoolConfig config = newConfig();
        PoolManager<Long> poolManager = newPoolManager(config, adapter);

        Assert.assertEquals(0L, (long) poolManager.allocate(this::dummyAllocate));
        for (int idx = 1; idx <= config.getIdMaximum(); idx++) {
            Assert.assertEquals(idx, (long) poolManager.allocate(this::dummyAllocate));
        }

        Assert.assertThrows(ResourceNotAvailableException.class, () -> poolManager.allocate(this::dummyAllocate));
    }

    @Test
    public void testChunkSelectionOnCollision() {
        PoolConfig config = newConfig();
        InMemorySetPoolEntityAdapter adapter = new InMemorySetPoolEntityAdapter();
        long chunkSize = (config.getIdMaximum() - config.getIdMinimum()) / config.getChunksCount();
        Assert.assertTrue(1 < config.getChunksCount());
        Assert.assertTrue(1 < chunkSize);

        for (long idx = config.getIdMinimum() + 1; idx < chunkSize + 1; idx++) {
            adapter.allocateSpecificId(idx);
        }

        PoolManager<Long> poolManager = newPoolManager(config, adapter);
        Assert.assertEquals(config.getIdMinimum(), (long) poolManager.allocate(this::dummyAllocate));
        Assert.assertEquals(chunkSize + 1, (long) poolManager.allocate(this::dummyAllocate));
    }

    @Test
    public void testAllocateReleasedResources() {
        PoolConfig config = newConfig();
        InMemorySetPoolEntityAdapter adapter = new InMemorySetPoolEntityAdapter();
        long chunkSize = (config.getIdMaximum() - config.getIdMinimum()) / config.getChunksCount();
        Assert.assertTrue(1 < config.getChunksCount());
        Assert.assertTrue(1 < chunkSize);

        for (long idx = config.getIdMinimum(); idx <= config.getIdMaximum(); idx++) {
            adapter.allocateSpecificId(idx);
        }

        PoolManager<Long> poolManager = newPoolManager(config, adapter);
        Assert.assertThrows(ResourceNotAvailableException.class, () -> poolManager.allocate(this::dummyAllocate));

        adapter.release(config.getIdMinimum() + 1);
        Assert.assertEquals(config.getIdMinimum() + 1, (long) poolManager.allocate(this::dummyAllocate));

        Assert.assertThrows(ResourceNotAvailableException.class, () -> poolManager.allocate(this::dummyAllocate));
    }

    @Test
    public void testAllChunksAccessibilityUsingFirstEntry() {
        PoolConfig config = newConfig();
        InMemorySetPoolEntityAdapter adapter = new InMemorySetPoolEntityAdapter();
        long chunkSize = (config.getIdMaximum() - config.getIdMinimum()) / config.getChunksCount();
        for (long idx = 0; idx <= config.getIdMaximum(); idx++) {
            if ((idx % chunkSize) == 0) {
                continue;
            }
            adapter.allocateSpecificId(idx);
        }

        PoolManager<Long> poolManager = newPoolManager(config, adapter);
        for (long idx = 0; idx <= config.getChunksCount(); idx++) {
            Long entry = poolManager.allocate(this::dummyAllocate);
            Assert.assertEquals(idx * chunkSize, (long) entry);
        }
        Assert.assertThrows(ResourceNotAvailableException.class, () -> poolManager.allocate(this::dummyAllocate));
    }

    @Test
    public void testAllChunksAccessibilityUsingLastEntry() {
        PoolConfig config = newConfig();
        InMemorySetPoolEntityAdapter adapter = new InMemorySetPoolEntityAdapter();
        long chunkSize = (config.getIdMaximum() - config.getIdMinimum()) / config.getChunksCount();
        for (long idx = 0; idx < config.getIdMaximum(); idx++) {
            if (idx < chunkSize * (config.getChunksCount() - 1) && (idx % chunkSize) == chunkSize - 1) {
                continue;
            }
            adapter.allocateSpecificId(idx);
        }

        PoolManager<Long> poolManager = newPoolManager(config, adapter);
        for (long idx = 0; idx < config.getChunksCount() - 1; idx++) {
            Long entry = poolManager.allocate(this::dummyAllocate);
            Assert.assertEquals((idx + 1) * chunkSize - 1, (long) entry);
        }
        Assert.assertEquals(config.getIdMaximum(), (long) poolManager.allocate(this::dummyAllocate));
        Assert.assertThrows(ResourceNotAvailableException.class, () -> poolManager.allocate(this::dummyAllocate));
    }

    @Test
    public void testSmallestChunkSize() {
        PoolConfig config = new PoolConfig(0, 5, 5);
        InMemorySetPoolEntityAdapter adapter = new InMemorySetPoolEntityAdapter();
        PoolManager<Long> poolManager = newPoolManager(config, adapter);
        for (int idx = 0; idx <= config.getIdMaximum(); idx++) {
            Assert.assertEquals(idx, (long) poolManager.allocate(this::dummyAllocate));
        }
        Assert.assertThrows(ResourceNotAvailableException.class, () -> poolManager.allocate(this::dummyAllocate));
    }

    private Long dummyAllocate(Long entityId) {
        return entityId;
    }

    private PoolManager.PoolConfig newConfig() {
        return new PoolManager.PoolConfig(0, 20, 4);
    }

    private PoolManager<Long> newPoolManager(PoolManager.PoolConfig config, PoolEntityAdapter adapter) {
        return new PredictablePoolManager<>(config, adapter);
    }
}
