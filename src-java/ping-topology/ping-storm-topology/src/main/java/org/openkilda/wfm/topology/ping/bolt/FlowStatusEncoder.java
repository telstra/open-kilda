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

package org.openkilda.wfm.topology.ping.bolt;

import org.openkilda.messaging.MessageData;
import org.openkilda.messaging.info.flow.FlowPingReport;
import org.openkilda.messaging.model.PingReport;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.bolt.KafkaEncoder;

import org.apache.storm.tuple.Tuple;

public class FlowStatusEncoder extends KafkaEncoder {
    public static final String BOLT_ID = ComponentId.FLOW_STATUS_ENCODER.toString();

    public static final String FIELD_ID_PING_REPORT = "flow_status";

    @Override
    protected MessageData pullPayload(Tuple input) throws PipelineException {
        PingReport pingReport = pullValue(input, FIELD_ID_PING_REPORT, PingReport.class);
        return new FlowPingReport(pingReport);
    }
}
