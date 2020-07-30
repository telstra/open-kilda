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

package org.openkilda.wfm.topology.floodlightrouter;

public final class Stream {

    public static final String KILDA_TOPO_DISCO = "KILDA_TOPO_DISCO";
    public static final String DISCO_REPLY = "DISCO_REPLY";
    public static final String SPEAKER_PING = "SPEAKER_PING";
    public static final String KILDA_SWITCH_MANAGER = "KILDA_SWITCH_MANAGER";
    public static final String SPEAKER_DISCO = "SPEAKER_DISCO";
    public static final String NORTHBOUND_REPLY = "NORTHBOUND_REPLY";
    public static final String REGION_NOTIFICATION = "REGION_NOTIFICATION";
    public static final String NB_WORKER = "NB_WORKER";

    private Stream() {}
}
