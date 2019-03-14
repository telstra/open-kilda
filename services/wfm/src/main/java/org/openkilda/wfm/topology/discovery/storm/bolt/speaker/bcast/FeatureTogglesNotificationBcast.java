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

package org.openkilda.wfm.topology.discovery.storm.bolt.speaker.bcast;

import org.openkilda.model.FeatureToggles;

public class FeatureTogglesNotificationBcast extends SpeakerBcast {
    private final FeatureToggles toggles;

    public FeatureTogglesNotificationBcast(FeatureToggles toggles) {
        this.toggles = toggles;
    }

    @Override
    public void apply(ISpeakerBcastConsumer handler) {
        handler.processFeatureTogglesUpdate(toggles);
    }
}
