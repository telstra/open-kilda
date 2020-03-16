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

package org.openkilda.constants;

/**
 * The entity OPENTSDB Interface.
 *
 * @author Gaurav Chugh
 *
 */
public abstract class OpenTsDb {

    public static final String GROUP_BY = "true";
    public static final String TYPE = "literal_or";
    public static final String TYPE_WILDCARD = "wildcard";
    public static final String RATE = "true";
    public static final String AGGREGATOR = "sum";

    public enum StatsType {
        SWITCH, PORT, FLOW, ISL, ISL_LOSS_PACKET, 
        FLOW_LOSS_PACKET, FLOW_RAW_PACKET, SWITCH_PORT, METER
    }

}
