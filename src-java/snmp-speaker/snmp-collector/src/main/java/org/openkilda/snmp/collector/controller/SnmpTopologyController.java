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

package org.openkilda.snmp.collector.controller;

import org.openkilda.snmp.collector.collection.data.SnmpHost;
import org.openkilda.snmp.collector.metrics.SnmpMetricCollector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/hosts", produces = "application/json;charset=UTF-8")
public class SnmpTopologyController {
    private static Logger LOG = LoggerFactory.getLogger(SnmpTopologyController.class);

    @Autowired
    private SnmpMetricCollector snmpMetricCollector;

    /**
     * Retrieve the current Hosts configured.
     *
     * @return a list of currently configured SnmpHost.
     */
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<SnmpHost> getHosts() {

        return snmpMetricCollector.getHosts();
    }

    /**
     * Update with the newly added hosts for SNMP metric collection.
     *
     * @param hosts the newly added/activated hosts
     * @return Map containing a status message
     */
    @PostMapping(path = "/add")
    @ResponseStatus(HttpStatus.OK)
    public List<SnmpHost> addHosts(@RequestBody List<SnmpHost> hosts) {
        snmpMetricCollector.addHosts(hosts);
        LOG.info("Received updates to add hosts for metric collection.");

        return snmpMetricCollector.getHosts();
    }

    /**
     * Remove the SnmpHosts from SNMP metrics collection.
     *
     * @param hosts A list of SnmpHost to remove
     * @return Map containing a status message
     */
    @PostMapping(path = "/remove")
    @ResponseStatus(HttpStatus.OK)
    public List<SnmpHost> removeHosts(@RequestBody List<SnmpHost> hosts) {
        snmpMetricCollector.removeHosts(hosts);
        LOG.info("Received updates to remove hosts from metric collection.");

        return snmpMetricCollector.getHosts();
    }

    @PostMapping(path = "/clear")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearHosts() {
        snmpMetricCollector.clearHosts();
        LOG.info("Received requests to clear all hosts from metric collection");
    }
}
