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

package org.openkilda.wfm.topology.isllatency.service;

import org.openkilda.messaging.info.event.IslOneWayLatency;

import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.isllatency.carriers.OneWayLatencyManipulationCarrier;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class OneWayLatencyManipulationService {
    public static final long ONE_MILLISECOND_IN_NANOSECONDS = TimeUnit.MILLISECONDS.toNanos(1);
    public static final int ONE_WAY_LATENCY_MULTIPLIER = 2;
    private OneWayLatencyManipulationCarrier carrier;

    public OneWayLatencyManipulationService(OneWayLatencyManipulationCarrier carrier) {
        this.carrier = carrier;
    }

    /**
     * Change latency of islOneWayLatency.
     *
     * @param data islOneWayLatency
     */
    public void handleOneWayLatency(IslOneWayLatency data) throws PipelineException {
        long latency = data.getLatency();

        if (latency <= 0) {
            log.info("Received not positive one way latency {} for ISL {}_{} ===> {}_{}. Packet Id: {}",
                    data.getLatency(), data.getSrcSwitchId(), data.getSrcPortNo(),
                    data.getDstSwitchId(), data.getDstPortNo(), data.getPacketId());
            latency = ONE_MILLISECOND_IN_NANOSECONDS;

        } else {
            latency = latency * ONE_WAY_LATENCY_MULTIPLIER; // converting one way latency to round trip latency
        }

        data.setLatency(latency);
        carrier.emitIslOneWayLatency(data);
    }
}
