/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.ping.model;

import lombok.Getter;

public class ShapingBurstCalculator {
    @Getter
    private long burstAmount = 0;
    private final int windowSize;
    private final WindowedCounter entityCounter;

    public ShapingBurstCalculator(int windowSize) {
        this.windowSize = windowSize;
        entityCounter = new WindowedCounter(windowSize);
    }

    public void increment() {
        entityCounter.increment();
    }

    /**
     * Must be called at the end of time frame.
     *
     * <p>Recalculate burstAmount
     */
    public void slide() {
        entityCounter.slide();

        final long totalCount = entityCounter.getCount();
        burstAmount = totalCount / windowSize;
        if ((totalCount % windowSize) != 0) {
            burstAmount += 1;
        }
    }
}
