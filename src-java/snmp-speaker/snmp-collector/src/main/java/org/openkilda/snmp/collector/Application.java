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

package org.openkilda.snmp.collector;

import org.openkilda.snmp.collector.metrics.SnmpMetricCollector;
import org.openkilda.snmp.collector.metrics.SnmpMetricWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
@Configuration
public class Application implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    @Autowired
    private SnmpMetricWriter metricWriter;
    @Autowired
    private SnmpMetricCollector metricCollector;

    /**
     * The main method, program entry point.
     *
     * @param args the program arguments
     * @throws Exception the exception
     */
    public static void main(String[] args) {

        try {
            SpringApplication app = new SpringApplication(Application.class);
            app.setBannerMode(Banner.Mode.OFF);
            app.run(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Start the metric collector task and metric writer task.
     *
     * @param args Command Line arguments passed in
     */
    @Override
    public void run(String... args) throws Exception {
        // start metric writer thread
        Thread metricWriterThread = new Thread(metricWriter, "snmp-metric-writer");
        metricWriterThread.start();
        LOG.info("Started metric writer task: {}", metricWriterThread.getName());

        // start metric collector
        metricCollector.start();
        LOG.info("Started SNMP metric collector task");
    }

}
