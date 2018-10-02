/* Copyright 2017 Telstra Open Source
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

package org.openkilda.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This class contains Kilda-specific Kafka components names for message destination.
 */
public enum Destination {
    /**
     * Northbound component.
     */
    NORTHBOUND("NORTHBOUND"),

    /**
     * Topology-Engine component.
     */
    TOPOLOGY_ENGINE("TOPOLOGY_ENGINE"),

    /**
     * Controller component.
     */
    CONTROLLER("CONTROLLER"),

    /**
     * WorkFlow Manager component.
     */
    WFM("WFM"),

    /**
     * WorkFlow Manager stats handling bolt.
     */
    WFM_STATS("WFM_STATS"),

    /**
     * WorkFlow Manager cache handling bolt.
     */
    WFM_CACHE("WFM_CACHE"),

    /**
     * WorkFlow Manager reroute handling bolt.
     */
    WFM_REROUTE("WFM_REROUTE"),

    /**
     * WorkFlow Manager transactions handling bolt.
     */
    WFM_TRANSACTION("WFM_TRANSACTION"),

    WFM_OF_DISCOVERY("WFM_OF_DISCOVERY"),
    WFM_FLOW_LCM("WFM_FLOW_LCM"),

    WFM_CTRL("WFM_CTRL"),
    CTRL_CLIENT("CTRL_CLIENT");

    /**
     * Message destination.
     */
    @JsonProperty("destination")
    private final String destination;

    /**
     * Instance constructor.
     *
     * @param destination message destination
     */
    @JsonCreator
    Destination(final String destination) {
        this.destination = destination;
    }

    /**
     * Returns message destination.
     *
     * @return message destination
     */
    public String getType() {
        return this.destination;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return destination;
    }
}
