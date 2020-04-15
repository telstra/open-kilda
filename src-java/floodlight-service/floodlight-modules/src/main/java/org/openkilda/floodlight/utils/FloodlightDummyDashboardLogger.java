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

package org.openkilda.floodlight.utils;

import org.openkilda.reporting.AbstractDashboardLogger;

import com.google.common.collect.ImmutableMap;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;

import java.util.Map;

public class FloodlightDummyDashboardLogger extends AbstractDashboardLogger {
    public FloodlightDummyDashboardLogger(Logger logger) {
        super(logger);
    }

    /**
     * Log ISL discovery event.
     */
    public void onIslDiscovery(
            DatapathId ingressSwitchId, OFPort ingressPortNumber, DatapathId egressSwitchId, OFPort egressPort,
            long latencyMs, Long packetId, long ofXid) {
        Map<String, String> context = ImmutableMap.of(
                "latency_ms", String.valueOf(latencyMs));

        String message = String.format("link discovered: %s-%d ===( %d ms )===> %s-%d id:%s OF-xid:%d",
                ingressSwitchId, ingressPortNumber.getPortNumber(), latencyMs,
                egressSwitchId, egressPort.getPortNumber(), packetId, ofXid);
        invokeLogger(message, context);
    }
}
