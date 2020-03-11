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

package org.openkilda.wfm.share.flow.resources;

import java.util.Random;

public final class ResourceUtils {
    /**
     * Return a random integer value between minValue and maxValue.
     */
    public static int computeStartValue(int minValue, int maxValue) {
        return new Random().ints(1, minValue, maxValue).iterator().nextInt();
    }

    /**
     * Return a random long value between minValue and maxValue.
     */
    public static long computeStartValue(long minValue, long maxValue) {
        return new Random().longs(1, minValue, maxValue).iterator().nextLong();
    }

    private ResourceUtils() {
        throw new UnsupportedOperationException();
    }
}
