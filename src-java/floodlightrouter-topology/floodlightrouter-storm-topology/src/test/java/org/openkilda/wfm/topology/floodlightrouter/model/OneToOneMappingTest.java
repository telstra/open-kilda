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

package org.openkilda.wfm.topology.floodlightrouter.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.openkilda.model.SwitchId;
import org.openkilda.stubs.ManualClock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class OneToOneMappingTest {
    private static final SwitchId SWITCH_ALPHA = new SwitchId(1);
    private static final SwitchId SWITCH_BETA = new SwitchId(2);
    private static final String REGION_A = "region_a";

    private final ManualClock clock = new ManualClock();
    private static final Duration staleDelay = Duration.ofSeconds(10);

    @Test
    public void testRemove() {
        OneToOneMapping subject = makeSubject();

        subject.add(SWITCH_ALPHA, REGION_A);
        subject.add(SWITCH_BETA, REGION_A);
        subject.remove(SWITCH_ALPHA);

        Optional<String> result = subject.lookup(SWITCH_ALPHA);
        assertTrue(result.isPresent());
        assertEquals(REGION_A, result.get());

        assertTrue(subject.lookup(SWITCH_ALPHA).isPresent());  // remove is delayed
        clock.adjust(staleDelay.plus(Duration.ofSeconds(1)));
        assertFalse(subject.lookup(SWITCH_ALPHA).isPresent());  // remove is delayed

        // active mapping visible despite removed marker
        assertTrue(subject.lookup(SWITCH_BETA).isPresent());
    }

    @Test
    public void testRemovedCleanup() {
        OneToOneMapping subject = makeSubject();

        // make 2 removed records
        subject.add(SWITCH_ALPHA, REGION_A);
        subject.remove(SWITCH_ALPHA);
        subject.add(SWITCH_BETA, REGION_A);
        subject.remove(SWITCH_BETA);

        assertTrue(subject.lookup(SWITCH_ALPHA).isPresent());
        assertTrue(subject.lookup(SWITCH_BETA).isPresent());

        clock.adjust(staleDelay.minus(Duration.ofSeconds(1)));

        // make lookup for one of 2 existing records
        Optional<String> result = subject.lookup(SWITCH_ALPHA);
        assertTrue(result.isPresent());
        assertEquals(REGION_A, result.get());

        clock.adjust(Duration.ofSeconds(2));

        // first record must be still alive, because of recent lookup for it
        result = subject.lookup(SWITCH_ALPHA);
        assertTrue(result.isPresent());
        assertEquals(REGION_A, result.get());

        // while second must go away
        assertFalse(subject.lookup(SWITCH_BETA).isPresent());

        clock.adjust(staleDelay.plus(Duration.ofSeconds(1)));
        // now first record must go away too
        assertFalse(subject.lookup(SWITCH_ALPHA).isPresent());
    }

    private OneToOneMapping makeSubject() {
        return new OneToOneMapping(clock, staleDelay);
    }
}
