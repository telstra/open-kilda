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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.Test;

public class ValidatorUtilsTest {

    @Test
    public void maxLatencyValidatorMustAcceptEqualMaxLatencyAndMaxLatencyTier2() throws InvalidFlowException {
        // should not raise exception
        ValidatorUtils.maxLatencyValidator(500L, 500L);
    }

    @Test
    public void maxLatencyValidatorThrowsExceptionIfMaxLatencyTier2HigherThanMaxLatency() throws InvalidFlowException {
        Exception exception = assertThrows(InvalidFlowException.class, () -> {
            ValidatorUtils.maxLatencyValidator(60000000L, 50000000L);
        });
        assertThat(exception.getMessage(), equalTo("The maxLatency 60ms is higher than maxLatencyTier2 50ms"));
    }

    @Test
    public void maxLatencyValidatorThrowsExceptionIfMaxLatencyNullAndMaxLatencyTier2Not() throws InvalidFlowException {
        Exception exception = assertThrows(InvalidFlowException.class, () -> {
            ValidatorUtils.maxLatencyValidator(null, 50000000L);
        });
        assertThat(exception.getMessage(), equalTo("maxLatencyTier2 property cannot be used without maxLatency"));
    }

    @Test
    public void maxLatencyValidatorMustAcceptMaxLatencyExistAndMaxLatencyTier2IsNull() throws InvalidFlowException {
        // should not raise exception
        ValidatorUtils.maxLatencyValidator(500L, null);
    }

}
