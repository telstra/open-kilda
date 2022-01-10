/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.stats.service;

import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.CookieBase;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.wfm.topology.stats.bolts.metrics.FlowDirectionHelper;
import org.openkilda.wfm.topology.stats.bolts.metrics.FlowDirectionHelper.Direction;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

public class TagsFormatter {
    private static final String UNKNOWN_TAG_VALUE = "unknown";  // TODO(surabujin): "(unknown)" should be better
    private static final String UNKNOWN_FLOW_ID_TAG_VALUE = UNKNOWN_TAG_VALUE;
    private static final String UNKNOWN_DIRECTION_TAG_VALUE = UNKNOWN_TAG_VALUE;
    private static final String UNKNOWN_COOKIE_TAG_VALUE = UNKNOWN_TAG_VALUE;

    @Getter
    private final Map<String, String> tags = new HashMap<>();

    public void addSwitchIdTag(SwitchId switchId) {
        tags.put("switchid", switchId.toOtsdFormat());
    }

    public void addFlowIdTag(String flowId) {
        tags.put("flowid", flowId != null ? flowId : UNKNOWN_FLOW_ID_TAG_VALUE);
    }

    public void addYFlowIdTag(String yFlowId) {
        tags.put("y_flow_id", yFlowId != null ? yFlowId : UNKNOWN_FLOW_ID_TAG_VALUE);
    }

    public void addDirectionTag(FlowPathDirection direction) {
        addDirectionTag(direction != null ? FlowDirectionHelper.mapDirection(direction) : null);
    }

    public void addDirectionTag(Direction direction) {
        tags.put("direction", direction != null ? direction.name().toLowerCase() : UNKNOWN_DIRECTION_TAG_VALUE);
    }

    public void addCookieTag(CookieBase cookie) {
        addCookieTag(cookie != null ? cookie.getValue() : null);
    }

    public void addCookieTag(Long cookie) {
        tags.put("cookie", cookie != null ? String.valueOf(cookie) : UNKNOWN_COOKIE_TAG_VALUE);
    }

    public void addCookieHexTag(CookieBase cookie) {
        tags.put("cookieHex", cookie != null ? String.valueOf(cookie) : UNKNOWN_COOKIE_TAG_VALUE);
    }

    public void addCookieTypeTag(CookieType type) {
        tags.put("type", type.name().toLowerCase());
    }

    public void addTableIdTag(long tableId) {
        tags.put("tableid", String.valueOf(tableId));
    }

    public void addMeterIdTag(long meterId) {
        tags.put("meterid", String.valueOf(meterId));
    }

    public void addInPortTag(long portNumber) {
        tags.put("inPort", String.valueOf(portNumber));
    }

    public void addOutPortTag(long portNumber) {
        tags.put("outPort", String.valueOf(portNumber));
    }

    public void addIsFlowSatelliteTag(boolean value) {
        tags.put("is_flow_satellite", mapTagValue(value));
    }

    public void addIsFlowRttInjectTag(boolean value) {
        tags.put("is_flowrtt_inject", mapTagValue(value));
    }

    public void addIsMirrorTag(boolean value) {
        tags.put("is_mirror", mapTagValue(value));
    }

    public void addIsLoopTag(boolean value) {
        tags.put("is_loop", mapTagValue(value));
    }

    public void addIsYFlowSubFlowTag(boolean value) {
        tags.put("is_y_flow_subflow", mapTagValue(value));
    }

    private static String mapTagValue(boolean value) {
        if (value) {
            return "true";
        } else {
            return "false";
        }
    }
}
