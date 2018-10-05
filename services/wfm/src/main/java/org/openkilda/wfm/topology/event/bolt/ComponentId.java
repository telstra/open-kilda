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

package org.openkilda.wfm.topology.event.bolt;

public enum ComponentId {
    MONOTONIC_TICK("monotonic.tick"),

    SPEAKER_SPOUT("disco-spout"),  // can't be changed, because used as part of consumer group
    SPEAKER_DECODER("speaker.decoder"),
    SPEAKER_ENCODER("speaker.encoder"),

    TE_ENCODER("te.encoder"),

    FL_MONITOR("fl-monitor"),

    ISL_DISCOVERY("isl-discovery");

    private final String value;

    ComponentId(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
