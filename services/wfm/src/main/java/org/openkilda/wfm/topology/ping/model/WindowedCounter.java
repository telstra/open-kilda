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

public class WindowedCounter {
    private long count = 0;
    private final long[] chunks;
    private int chunkIndex = 0;

    public WindowedCounter(int windowSize) {
        if (windowSize < 1) {
            throw new IllegalArgumentException(String.format(
                    "%s: Can\'t operate with requested window size == %d",
                    getClass().getCanonicalName(), windowSize));
        }

        chunks = new long[windowSize];
    }

    public void increment() {
        chunks[chunkIndex] += 1;
    }

    /**
     * Switch to the next frame.
     */
    public void slide() {
        final long current = chunks[chunkIndex++];
        if (chunkIndex == chunks.length) {
            chunkIndex = 0;
        }
        long replace = chunks[chunkIndex];
        chunks[chunkIndex] = 0;

        count -= replace;
        count += current;
    }

    public long getCount() {
        return count + chunks[chunkIndex];
    }
}
