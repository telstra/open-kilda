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

package org.openkilda.wfm.kafka;

import org.apache.commons.lang.StringUtils;
import org.apache.storm.kafka.spout.NamedSubscription;

import java.util.Collection;

/**
 * Custom implementation of storm's {@link NamedSubscription} with fix of incorrect topic names.
 */
public class CustomNamedSubscription extends NamedSubscription {

    public CustomNamedSubscription(Collection<String> topics) {
        super(topics);
    }

    public CustomNamedSubscription(String... topics) {
        super(topics);
    }

    @Override
    public String getTopicsString() {
        return StringUtils.join(this.topics, ",");
    }
}
