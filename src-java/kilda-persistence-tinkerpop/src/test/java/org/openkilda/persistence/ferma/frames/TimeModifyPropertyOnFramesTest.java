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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.SwitchRepository;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.function.CheckedSupplier;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;

public class TimeModifyPropertyOnFramesTest extends InMemoryGraphBasedTest {
    private SwitchRepository switchRepository;

    @Before
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

        assertNotEquals(timeModifyBefore, timeModifyAfter);
    }

    @Test
    public void shouldUpdateTimeModifyOnAnnotatedPropertyWithConverterChange() {
        Instant timeModifyBefore = transactionManager.doInTransaction(() -> createTestSwitch(1).getTimeModify());
        waitUntilNowIsAfter(timeModifyBefore);

        transactionManager.doInTransaction(() -> {
            switchRepository.findById(new SwitchId(1)).get().setStatus(SwitchStatus.REMOVED);
        });

        Instant timeModifyAfter = switchRepository.findById(new SwitchId(1)).get().getTimeModify();

        assertNotEquals(timeModifyBefore, timeModifyAfter);
    }

    @Test
    public void shouldUpdateTimeModifyOnImplementedPropertyChange() {
        Instant timeModifyBefore = transactionManager.doInTransaction(() -> createTestSwitch(1).getTimeModify());
        waitUntilNowIsAfter(timeModifyBefore);

        transactionManager.doInTransaction(() -> {
            switchRepository.findById(new SwitchId(1)).get().setSocketAddress(new InetSocketAddress(12345));
        });

        Instant timeModifyAfter = switchRepository.findById(new SwitchId(1)).get().getTimeModify();

        assertNotEquals(timeModifyBefore, timeModifyAfter);
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

        assertEquals(timeModifyBefore, timeModifyAfter);
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

        assertEquals(timeModifyBefore, timeModifyAfter);
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

        assertEquals(timeModifyBefore, timeModifyAfter);
    }

    private void waitUntilNowIsAfter(Instant before) {
        Failsafe.with(new RetryPolicy<Instant>().withDelay(Duration.ofMillis(1))
                .handleResultIf(result -> !before.isBefore(result)))
                .get((CheckedSupplier<Instant>) Instant::now);
    }
}
