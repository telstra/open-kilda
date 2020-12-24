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

package org.openkilda.wfm.share.zk;

import org.openkilda.bluegreen.LifecycleEvent;
import org.openkilda.bluegreen.ZkClient;
import org.openkilda.bluegreen.ZkStateTracker;
import org.openkilda.bluegreen.ZkWriter;
import org.openkilda.wfm.AbstractBolt;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * This bolt is responsible for writing data into ZooKeeper.
 */
public class ZooKeeperBolt extends AbstractBolt {
    public static final String BOLT_ID = "zookeeper.bolt";
    public static final String FIELD_ID_STATE = "lifecycle.state";

    public static final String FIELD_ID_CONTEXT = AbstractBolt.FIELD_ID_CONTEXT;
    private String id;
    private String serviceName;
    private String connectionString;
    private Instant zooKeeperConnectionTimestamp = Instant.MIN;
    private transient ZkWriter zkWriter;
    private transient ZkStateTracker zkStateTracker;

    public ZooKeeperBolt(String id, String serviceName, String connectionString) {
        this.id = id;
        this.serviceName = serviceName;
        this.connectionString = connectionString;
    }


    protected boolean isZooKeeperConnectTimeoutPassed() {
        return zooKeeperConnectionTimestamp.plus(10, ChronoUnit.SECONDS)
                .isBefore(Instant.now());
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        if (!zkWriter.isConnectedAndValidated()) {
            if (isZooKeeperConnectTimeoutPassed()) {
                zkWriter.safeRefreshConnection();
                zooKeeperConnectionTimestamp = Instant.now();
            }
        }
        try {
            LifecycleEvent event = (LifecycleEvent) input.getValueByField(FIELD_ID_STATE);
            if (event != null) {
                zkStateTracker.processLifecycleEvent(event);
            } else {
                log.error("Received null value as a lifecycle-event");
            }

        } catch (Exception e) {
            log.error("Failed to process event: {}", e.getMessage(), e);
        }

    }

    @Override
    protected void init() {
        initZk();
    }

    private void initZk() {
        zkWriter = ZkWriter.builder().id(id).serviceName(serviceName)
                .connectionRefreshInterval(ZkClient.DEFAULT_CONNECTION_REFRESH_INTERVAL)
                .connectionString(connectionString).build();
        zkWriter.init();
        zkStateTracker = new ZkStateTracker(zkWriter);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {

    }
}
