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

package org.openkilda.snmp.collector.metrics;

import org.openkilda.messaging.snmp.metric.SnmpMetricData;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;

@Component
@Profile("dev")
public class SnmpMetricConsoleWriter implements SnmpMetricWriter {
    @Autowired
    private BlockingQueue<SnmpMetricData> metricQueue;

    @Override
    public void run() {
        while (true) {
            try {
                SnmpMetricData metricData = metricQueue.take();
                System.out.format("Received metric data %s\n", metricData.toString());
            } catch (InterruptedException e) {
                System.out.format("%s interrupted while waiting for data\n", this.getClass().getName());
            }
        }
    }
}
