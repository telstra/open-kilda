/* Copyright 2019 Telstra Open Source
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

import org.junit.Assert;
import org.junit.Test;

public class FlowEndpointTest {
    @Test
    public void detectConflict() {
        SwitchId swAlpha = new SwitchId(1);
        SwitchId swBeta = new SwitchId(2);

        Assert.assertTrue(
                detectConflict(
                        new FlowEndpoint(swAlpha, 1),
                        new FlowEndpoint(swAlpha, 1)));
        Assert.assertTrue(
                detectConflict(
                        new FlowEndpoint(swAlpha, 1, 2),
                        new FlowEndpoint(swAlpha, 1, 2)));
        Assert.assertTrue(
                detectConflict(
                        new FlowEndpoint(swAlpha, 1, 2, 3),
                        new FlowEndpoint(swAlpha, 1, 2, 3)));

        Assert.assertFalse(
                detectConflict(
                        new FlowEndpoint(swAlpha, 1),
                        new FlowEndpoint(swBeta, 1)));

        Assert.assertFalse(
                detectConflict(
                        new FlowEndpoint(swAlpha, 1, 2),
                        new FlowEndpoint(swAlpha, 1)));
        Assert.assertFalse(
                detectConflict(
                        new FlowEndpoint(swAlpha, 1, 2, 3),
                        new FlowEndpoint(swAlpha, 1)));
        Assert.assertFalse(
                detectConflict(
                        new FlowEndpoint(swAlpha, 1),
                        new FlowEndpoint(swAlpha, 1, 2)));
        Assert.assertFalse(
                detectConflict(
                        new FlowEndpoint(swAlpha, 1),
                        new FlowEndpoint(swAlpha, 1, 2, 3)));

        Assert.assertFalse(
                detectConflict(
                        new FlowEndpoint(swAlpha, 1, 2, 3),
                        new FlowEndpoint(swAlpha, 1, 2)));
        Assert.assertFalse(
                detectConflict(
                        new FlowEndpoint(swAlpha, 1, 2),
                        new FlowEndpoint(swAlpha, 1, 2, 3)));
    }

    private boolean detectConflict(FlowEndpoint alpha, FlowEndpoint beta) {
        return alpha.detectConflict(beta);
    }
}
