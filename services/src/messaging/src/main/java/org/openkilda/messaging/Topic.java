/* Copyright 2017 Telstra Open Source
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

package org.openkilda.messaging;

/**
 * This class contains Kilda-specific Kafka topics.
 */
@Deprecated
public interface Topic {

    public static final String CTRL = "kilda.ctrl";
    public static final String FLOW = "kilda.flow";
    public static final String HEALTH_CHECK = "kilda.health.check";
    public static final String NORTHBOUND = "kilda.northbound";
    public static final String OTSDB = "kilda.otsdb";
    public static final String SIMULATOR = "kilda.simulator";
    public static final String SPEAKER = "kilda.speaker";
    public static final String STATS = "kilda.stats";
    public static final String TOPO_CACHE = "kilda.topo.cache";
    public static final String TOPO_DISCO = "kilda.topo.disco";
    public static final String TOPO_ENG = "kilda.topo.eng";

}
