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

package org.openkilda.model;

import org.junit.Test;

public class FlowEndpointTest {
    @Test
    public void testEndpointConflicts() {
        SwitchId swAlpha = new SwitchId(1);
        SwitchId swBeta = new SwitchId(2);

        processConflictTest(new FlowEndpoint(swAlpha, 1), new FlowEndpoint(swBeta, 2), false);
        processConflictTest(new FlowEndpoint(swAlpha, 1), new FlowEndpoint(swAlpha, 2), false);

        processConflictTest(new FlowEndpoint(swAlpha, 1), new FlowEndpoint(swAlpha, 1), true);
        processConflictTest(new FlowEndpoint(swAlpha, 1), new FlowEndpoint(swAlpha, 1, 10), false);
        processConflictTest(new FlowEndpoint(swAlpha, 1), new FlowEndpoint(swAlpha, 1, 10, 20), false);
        processConflictTest(new FlowEndpoint(swAlpha, 1, 10), new FlowEndpoint(swAlpha, 1), false);
        processConflictTest(new FlowEndpoint(swAlpha, 1, 10), new FlowEndpoint(swAlpha, 1, 10), true);
        processConflictTest(new FlowEndpoint(swAlpha, 1, 10), new FlowEndpoint(swAlpha, 1, 10, 20), true);
        processConflictTest(new FlowEndpoint(swAlpha, 1, 10, 20), new FlowEndpoint(swAlpha, 1), false);
        processConflictTest(new FlowEndpoint(swAlpha, 1, 10, 20), new FlowEndpoint(swAlpha, 1, 10), true);
        processConflictTest(new FlowEndpoint(swAlpha, 1, 10, 20), new FlowEndpoint(swAlpha, 1, 10, 20), true);
        processConflictTest(new FlowEndpoint(swAlpha, 1, 10, 20), new FlowEndpoint(swAlpha, 1, 10, 30), false);
        processConflictTest(new FlowEndpoint(swAlpha, 1, 10, 30), new FlowEndpoint(swAlpha, 1, 10, 20), false);

        processConflictTest(new FlowEndpoint(swAlpha, 1, 30), new FlowEndpoint(swAlpha, 1, 10), false);
        processConflictTest(new FlowEndpoint(swAlpha, 1, 10), new FlowEndpoint(swAlpha, 1, 30), false);
    }

    private void processConflictTest(FlowEndpoint left, FlowEndpoint right, boolean expectConflict) {
        boolean isConflict = left.isConflict(right);
        if (isConflict != expectConflict) {
            throw new AssertionError(String.format(
                    "Endpoint %s and %s must %s, but they don't", left, right,
                    expectConflict ? "conflict" : "not conflict"));
        }
    }
}
