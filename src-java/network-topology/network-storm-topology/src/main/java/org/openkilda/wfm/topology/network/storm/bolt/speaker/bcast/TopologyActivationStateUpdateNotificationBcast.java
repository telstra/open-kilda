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

package org.openkilda.wfm.topology.network.storm.bolt.speaker.bcast;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@DefaultSerializer(FieldSerializer.class)
@Data
public class TopologyActivationStateUpdateNotificationBcast extends SpeakerBcast {
    private boolean isActive;

    public TopologyActivationStateUpdateNotificationBcast(boolean isActive) {
        this.isActive = isActive;
    }

    @Override
    public void apply(ISpeakerBcastConsumer handler) {
        handler.activationStatusUpdate(isActive);
    }
}
