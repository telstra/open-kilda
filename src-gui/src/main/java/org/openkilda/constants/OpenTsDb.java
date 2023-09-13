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

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * The entity OPENTSDB Interface.
 *
 * @author Gaurav Chugh
 */
public abstract class OpenTsDb {

    public static final boolean GROUP_BY = true;
    public static final String TYPE = "literal_or";
    public static final String TYPE_WILDCARD = "wildcard";
    public static final boolean RATE = true;
    public static final String AGGREGATOR = "sum";

    @Getter
    public enum StatsType {
        SWITCH("switch"),
        PORT("port"),
        FLOW("flow"),
        ISL("isl"),
        ISL_LOSS_PACKET("islLossPacket"),
        FLOW_LOSS_PACKET("flowLossPacket"),
        FLOW_RAW_PACKET("flowRawPacket"),
        SWITCH_PORT("switchPort"),
        METER("meter");

        @JsonValue
        private final String jsonValue;

        StatsType(String jsonValue) {
            this.jsonValue = jsonValue;
        }

        private static final Map<String, StatsType> jsonValueToStatsTypeMap = new HashMap<>();

        static {
            for (StatsType statsType : values()) {
                jsonValueToStatsTypeMap.put(statsType.getJsonValue(), statsType);
            }
        }

        public static StatsType byJsonValue(String jsonValue) {
            return jsonValue != null ? jsonValueToStatsTypeMap.get(jsonValue) : null;
        }
    }
}
