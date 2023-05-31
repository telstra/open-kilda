/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.ping.bolt.producer;

import org.openkilda.adapter.FlowDestAdapter;
import org.openkilda.adapter.FlowSourceAdapter;
import org.openkilda.messaging.model.FlowDirection;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.model.Ping;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.wfm.topology.ping.model.PingContext;

import com.google.common.collect.Lists;

import java.util.List;

public class FlowPingContextProducer implements PingContextProducer {

    @Override
    public List<PingContext> produce(PingContext pingContext) {
        return Lists.newArrayList(
                pingContext.toBuilder().ping(
                        buildPing(pingContext, FlowDirection.FORWARD)).direction(FlowDirection.FORWARD).build(),
                pingContext.toBuilder().ping(
                        buildPing(pingContext, FlowDirection.REVERSE)).direction(FlowDirection.REVERSE).build());
    }

    private Ping buildPing(PingContext pingContext, FlowDirection direction) {
        Flow flow = pingContext.getFlow();
        FlowEndpoint ingress;
        FlowEndpoint egress;
        int islPort;
        if (FlowDirection.FORWARD == direction) {
            ingress = new FlowSourceAdapter(flow).getEndpoint();
            egress = new FlowDestAdapter(flow).getEndpoint();
            islPort = ProducerUtils.getFirstIslPort(flow.getForwardPath());
        } else if (FlowDirection.REVERSE == direction) {
            ingress = new FlowDestAdapter(flow).getEndpoint();
            egress = new FlowSourceAdapter(flow).getEndpoint();
            islPort = ProducerUtils.getFirstIslPort(flow.getReversePath());
        } else {
            throw new IllegalArgumentException(String.format(
                    "Unexpected %s value: %s", FlowDirection.class.getCanonicalName(), direction));
        }

        return new Ping(new NetworkEndpoint(ingress.getSwitchId(), ingress.getPortNumber()),
                new NetworkEndpoint(egress.getSwitchId(), egress.getPortNumber()),
                pingContext.getTransitEncapsulation(), islPort);
    }
}
