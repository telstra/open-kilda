/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.opentsdb.client.telnet;

import com.google.common.collect.Lists;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;

public class ReconnectDelayProvider {
    private final Iterator<Duration> delayIterator;

    private final Duration repeat;

    public ReconnectDelayProvider(Duration delay, Duration... extra) {
        List<Duration> sequence = Lists.asList(delay, extra);

        delayIterator = sequence.iterator();
        repeat = sequence.get(sequence.size() - 1);
    }

    /**
     * Provide next delay value.
     */
    public Duration provide() {
        if (delayIterator.hasNext()) {
            return delayIterator.next();
        }
        return repeat;
    }
}
