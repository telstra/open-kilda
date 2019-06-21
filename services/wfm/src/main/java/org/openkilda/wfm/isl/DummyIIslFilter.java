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

package org.openkilda.wfm.isl;

import org.openkilda.messaging.model.DiscoveryLink;
import org.openkilda.model.SwitchId;

import java.util.HashSet;
import java.util.Set;

public class DummyIIslFilter implements IIslFilter {
    private final Set<DiscoveryLink> matchSet;

    public DummyIIslFilter() {
        this.matchSet = new HashSet<>();
    }

    public void add(SwitchId switchId, int portId) {
        DiscoveryLink match = new DiscoveryLink(switchId, portId, 1, DiscoveryLink.ENDLESS_ATTEMPTS);
        matchSet.add(match);
    }

    public void clear() {
        matchSet.clear();
    }

    @Override
    public boolean isMatch(DiscoveryLink subject) {
        return matchSet.contains(subject);
    }

    public Set<DiscoveryLink> getMatchSet() {
        return matchSet;
    }
}
