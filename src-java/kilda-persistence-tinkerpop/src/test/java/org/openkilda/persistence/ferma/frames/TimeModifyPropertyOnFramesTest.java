/* Copyright 2020 Telstra Open Source
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

package org.openkilda.persistence.ferma.frames;

import org.openkilda.model.IpSocketAddress;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.SwitchRepository;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import dev.failsafe.function.CheckedSupplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

public class TimeModifyPropertyOnFramesTest extends InMemoryGraphBasedTest {
    private SwitchRepository switchRepository;

    @BeforeEach
    public void setUp() {
        switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
    }

    @Test
    public void shouldUpdateTimeModifyOnAnnotatedPropertyChange() {
        Instant timeModifyBefore = transactionManager.doInTransaction(() -> createTestSwitch(1).getTimeModify());
        waitUntilNowIsAfter(timeModifyBefore);

        transactionManager.doInTransaction(() -> {
            switchRepository.findById(new SwitchId(1)).get().setDescription("updated description");
        });

        Instant timeModifyAfter = switchRepository.findById(new SwitchId(1)).get().getTimeModify();

        Assertions.assertNotEquals(timeModifyBefore, timeModifyAfter);
    }

    @Test
    public void shouldUpdateTimeModifyOnAnnotatedPropertyWithConverterChange() {
        Instant timeModifyBefore = transactionManager.doInTransaction(() -> createTestSwitch(1).getTimeModify());
        waitUntilNowIsAfter(timeModifyBefore);

        transactionManager.doInTransaction(() -> {
            switchRepository.findById(new SwitchId(1)).get().setStatus(SwitchStatus.REMOVED);
        });

        Instant timeModifyAfter = switchRepository.findById(new SwitchId(1)).get().getTimeModify();

        Assertions.assertNotEquals(timeModifyBefore, timeModifyAfter);
    }

    @Test
    public void shouldUpdateTimeModifyOnImplementedPropertyChange() {
        Instant timeModifyBefore = transactionManager.doInTransaction(() -> createTestSwitch(1).getTimeModify());
        waitUntilNowIsAfter(timeModifyBefore);

        transactionManager.doInTransaction(() -> switchRepository.findById(
                new SwitchId(1)).get().setSocketAddress(new IpSocketAddress("192.168.10.20", 12345)));

        Instant timeModifyAfter = switchRepository.findById(new SwitchId(1)).get().getTimeModify();

        Assertions.assertNotEquals(timeModifyBefore, timeModifyAfter);
    }

    @Test
    public void shouldNotUpdateTimeModifyOnOnAnnotatedPropertyAndSameValueChange() {
        Instant timeModifyBefore = transactionManager.doInTransaction(() -> createTestSwitch(1).getTimeModify());
        waitUntilNowIsAfter(timeModifyBefore);

        transactionManager.doInTransaction(() -> {
            Switch sw = switchRepository.findById(new SwitchId(1)).get();
            sw.setDescription(sw.getDescription());
        });

        Instant timeModifyAfter = switchRepository.findById(new SwitchId(1)).get().getTimeModify();

        Assertions.assertEquals(timeModifyBefore, timeModifyAfter);
    }

    @Test
    public void shouldNotUpdateTimeModifyOnAnnotatedPropertyWithConverterAndSameValueChange() {
        Instant timeModifyBefore = transactionManager.doInTransaction(() -> createTestSwitch(1).getTimeModify());
        waitUntilNowIsAfter(timeModifyBefore);

        transactionManager.doInTransaction(() -> {
            Switch sw = switchRepository.findById(new SwitchId(1)).get();
            sw.setStatus(sw.getStatus());
        });

        Instant timeModifyAfter = switchRepository.findById(new SwitchId(1)).get().getTimeModify();

        Assertions.assertEquals(timeModifyBefore, timeModifyAfter);
    }


    @Test
    public void shouldNotUpdateTimeModifyOnImplementedPropertyAndSameValueChange() {
        Instant timeModifyBefore = transactionManager.doInTransaction(() -> createTestSwitch(1).getTimeModify());
        waitUntilNowIsAfter(timeModifyBefore);

        transactionManager.doInTransaction(() -> {
            Switch sw = switchRepository.findById(new SwitchId(1)).get();
            sw.setSocketAddress(sw.getSocketAddress());
        });

        Instant timeModifyAfter = switchRepository.findById(new SwitchId(1)).get().getTimeModify();

        Assertions.assertEquals(timeModifyBefore, timeModifyAfter);
    }

    private void waitUntilNowIsAfter(Instant before) {
        Failsafe.with(RetryPolicy.<Instant>builder()
                        .withDelay(Duration.ofMillis(1))
                        .handleResultIf(result -> !before.isBefore(result))
                        .build())
                .get((CheckedSupplier<Instant>) Instant::now);
    }
}
