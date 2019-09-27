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

package org.openkilda.wfm.topology.network.storm;

public enum ComponentId {
    MONOTONIC_TICK("monotonic.tick"),

    INPUT_SPEAKER("input.speaker"),
    INPUT_SWMANAGER("input.swmanager"),
    INPUT_SPEAKER_FLOW("input.speaker.flow"),
    NETWORK_HISTORY("network-history"),

    WATCH_LIST("watch-list-handler"),
    WATCHER("watcher-handler"),
    DECISION_MAKER("decision-maker-handler"),

    SPEAKER_ROUTER("speaker.router"),
    SWMANAGER_ROUTER("swmanager.router"),
    SPEAKER_FLOW_ROUTER("speaker.flow.router"),
    SWITCH_HANDLER("switch-handler"),
    PORT_HANDLER("port-handler"),
    BFD_PORT_HANDLER("bfd-port-handler"),
    UNI_ISL_HANDLER("uni-isl-handler"),
    ISL_HANDLER("isl-handler"),

    HISTORY_HANDLER("history-handler"),

    SPEAKER_ENCODER("speaker.encoder"),
    SPEAKER_FLOW_ENCODER("speaker.flow.encoder"),
    SPEAKER_OUTPUT("speaker.output"),
    SPEAKER_FLOW_OUTPUT("speaker.flow.output"),

    SWMANAGER_ENCODER("swmanager.encoder"),
    SWMANAGER_OUTPUT("swmanager.output"),

    REROUTE_ENCODER("reroute.encoder"),
    REROUTE_OUTPUT("reroute.output"),

    STATUS_ENCODER("status.encoder"),
    STATUS_OUTPUT("status.output");

    private final String value;

    ComponentId(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
