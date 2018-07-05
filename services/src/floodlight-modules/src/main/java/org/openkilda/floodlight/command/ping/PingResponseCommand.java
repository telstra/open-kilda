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

package org.openkilda.floodlight.command.ping;

import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.error.CorruptedNetworkDataException;
import org.openkilda.floodlight.model.PingData;
import org.openkilda.messaging.floodlight.response.PingResponse;
import org.openkilda.messaging.model.PingMeters;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PingResponseCommand extends Abstract {
    private static final Logger log = LoggerFactory.getLogger(PingResponseCommand.class);

    private final DatapathId datapathId;
    private final long latency;
    private final byte[] payload;

    public PingResponseCommand(CommandContext context, DatapathId dpId, long latency, byte[] payload) {
        super(context);

        this.datapathId = dpId;
        this.latency = latency;
        this.payload = payload;
    }

    @Override
    public void execute() {
        PingData data;
        try {
            DecodedJWT token = getPingService().getSignature().verify(payload);
            data = PingData.of(token);
            getContext().setCorrelationId(data.getPingId().toString());
        } catch (CorruptedNetworkDataException e) {
            logPing.error(String.format("dpid:%s %s", datapathId, e));
            return;
        }

        if (!data.getDest().equals(datapathId)) {
            logPing.error("Catch ping package on %s while target is %s", datapathId, data.getDest());
            return;
        }

        PingMeters meters = data.produceMeasurements(latency);
        logCatch(data, meters);

        PingResponse response = new PingResponse(getContext().getCtime(), data.getPingId(), meters);
        sendResponse(response);
    }

    private void logCatch(PingData data, PingMeters meters) {
        String pingId = String.format("ping{%s}", data.getPingId().toString());

        String source = data.getSource().toString();
        if (data.getSourceVlan() != null) {
            source += "-" + data.getSourceVlan().toString();
        }
        logPing.info(
                "Catch ping {} ===( {}, latency: {}ms )===> {}",
                source, pingId, meters.getNetworkLatency(), data.getDest());
    }
}
