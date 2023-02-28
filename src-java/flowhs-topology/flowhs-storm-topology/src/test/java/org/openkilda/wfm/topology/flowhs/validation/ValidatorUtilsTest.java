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

package org.openkilda.wfm.topology.flowhs.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.openkilda.messaging.error.InvalidFlowException;
import org.openkilda.messaging.validation.ValidatorUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ValidatorUtilsTest {

    /**
     * If latency parameters are set, they must be non-negative numbers.
     * maxLatency must be less than or equal to maxLatencyTier2.
     * maxLatencyTier2 is valid only when maxLatency is valid.
     * Both parameters can be 0 or null to enable "erasing".
     * @return parameters for test in the format: {{must fail?} {maxLatency value} {maxLatencyTier2 value}}
     */
    @Parameterized.Parameters()
    public static Object[] data() {
        return new Object[][]{
                {false, null, null},
                {false, 0L, 0L},
                {false, 1000L, 1000L},
                {false, 1000L, 10000L},
                {false, 1000L, 0L},
                {false, 500L, null},
                {false, 0L, 1000L},
                {true, null, 50000000L},
                {true, -1000L, 1000L},
                {true, -1000L, -1000L},
                {true, 1000L, -1000L},
                {true, -1000L, 0L},
                {true, -1000L, null},
                {true, 1000L, 10L},
                {true, 60000000L, 50000000L}
        };
    }

    private final boolean mustFail;
    private final Long maxLatency;
    private final Long maxLatencyTier2;

    public ValidatorUtilsTest(boolean mustFail, Long maxLatency, Long maxLatencyTier2) {
        this.mustFail = mustFail;
        this.maxLatency = maxLatency;
        this.maxLatencyTier2 = maxLatencyTier2;
    }

    @Test
    public void verifyFlowValidatorForMaxLatencyParameters() {
        if (mustFail) {
            assertThatValidationThrows(maxLatency, maxLatencyTier2);
        } else {
            assertThatValidationPasses(maxLatency, maxLatencyTier2);
        }
    }

    private void assertThatValidationThrows(Long maxLatency, Long maxLatencyTier2) {
        assertThrows(InvalidFlowException.class, () ->
                ValidatorUtils.validateMaxLatencyAndLatencyTier(maxLatency, maxLatencyTier2));
    }

    private void assertThatValidationPasses(Long maxLatency, Long maxLatencyTier2) {
        assertDoesNotThrow(() -> ValidatorUtils.validateMaxLatencyAndLatencyTier(maxLatency, maxLatencyTier2));
    }

}
