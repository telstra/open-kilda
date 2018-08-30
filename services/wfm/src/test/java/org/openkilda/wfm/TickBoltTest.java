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

package org.openkilda.wfm;

import org.openkilda.wfm.topology.utils.AbstractTickStatefulBolt;
import org.openkilda.wfm.topology.utils.FileUtil;

import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.storm.LocalCluster;
import org.apache.storm.state.KeyValueState;
import org.apache.storm.testing.FeederSpout;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.utils.Utils;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * The Tick Tests exercise the core functionality of a OFELinkTickBolt.
 */
public class TickBoltTest {
    // Don't need the extra stuff that comes with the AbstractStormTest yet. Should refactor
    // so that we can pick and choose what's needed.
    // extends AbstractStormTest {

    /**
     * This class helps expose how ticks work, it can serve as a basis for something else.
     */
    private static class SimpleStatefulTick
            extends AbstractTickStatefulBolt<KeyValueState<String, ConcurrentHashMap<String, String>>> {

        public FileUtil tickFile = new FileUtil().withFileName("tick.log");
        public FileUtil workFile = new FileUtil().withFileName("work.log");

        protected SimpleStatefulTick() {
            super(2);
        }

        @Override
        protected void doTick(Tuple tuple) {
            tickFile.append("tick\n");
        }

        @Override
        protected void doWork(Tuple tuple) {
            workFile.append("work\n");
        }

        @Override
        public void initState(KeyValueState<String, ConcurrentHashMap<String, String>> state) {
        }
    }

    public static FeederSpout createFeeder() {
        return new FeederSpout(new Fields("key", "message"));
    }

    @Test
    public void basicTickTest() throws IOException {
        System.out.println("==> Starting BasicTickTest");

        String spoutId = "feeder.spout";
        String boltId = "tick.bolt";
        String topoId = "TestTopology";


        TopologyBuilder builder = new TopologyBuilder();
        FeederSpout spout = TickBoltTest.createFeeder();
        builder.setSpout(spoutId, spout);
        SimpleStatefulTick tickBolt = new SimpleStatefulTick();
        builder.setBolt(boltId, tickBolt).shuffleGrouping(spoutId);
        LocalCluster cluster = new LocalCluster();
        cluster.submitTopology(topoId, TestUtils.stormConfig(), builder.createTopology());

        /* Let's Submit Stuff! */
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
        spout.feed(Arrays.asList(new String[]{"key1", "msg1"}));

        /* And sleep some more */
        Uninterruptibles.sleepUninterruptibly(6, TimeUnit.SECONDS);

        // TODO: this test isn't great .. the number of lines in the file from ticks could vary
        int expectedLines = 3;
        Assert.assertTrue("We should have at least " + expectedLines + " lines in the test file.",
                expectedLines <= tickBolt.tickFile.numLines());
        Assert.assertEquals(1, tickBolt.workFile.numLines());

        cluster.killTopology(topoId);
        Utils.sleep(4 * 1000);
    }
}
