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

package org.openkilda.wfm.topology.grpc;

import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.grpc.bolt.GrpcRequestBuilderBolt;
import org.openkilda.wfm.topology.grpc.bolt.GrpcRequestSenderBolt;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.topology.TopologyBuilder;

final class GrpcClientTopology extends AbstractTopology<GrpcClientTopologyConfig> {

    private static final String SPOUT_ID = "grpc-spout";
    private static final String BUILDER_BOLT_ID = "grpc-request-builder";
    private static final String SENDER_BOLT_ID = "grpc-sender";
    private static final String KAFKA_BOLT_ID = "kafka-bolt";

    @Override
    public StormTopology createTopology() {
        TopologyBuilder builder = new TopologyBuilder();

        /*
        KafkaSpout spout = buildKafkaSpout(topologyConfig.getKafkaGrpcTopic(), SPOUT_ID);
        builder.setSpout(SPOUT_ID, spout, topologyConfig.getParallelism());
        */

        GrpcRequestBuilderBolt requestBuilder =
                new GrpcRequestBuilderBolt(getConfig().getDefaultUsername(), getConfig().getDefaultPassword());
        builder.setBolt(BUILDER_BOLT_ID, requestBuilder)
                .shuffleGrouping(SPOUT_ID);

        builder.setBolt(SENDER_BOLT_ID, new GrpcRequestSenderBolt())
                .shuffleGrouping(BUILDER_BOLT_ID);

        /*
        builder.setBolt(KAFKA_BOLT_ID, buildKafkaBolt(topologyConfig.getKafkaBfdTopic()))
                .shuffleGrouping(SENDER_BOLT_ID);
        */

        return builder.createTopology();
    }

    private GrpcClientTopology(LaunchEnvironment env) {
        super(env, GrpcClientTopologyConfig.class);
    }

    /**
     * Topology entry point.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new GrpcClientTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
