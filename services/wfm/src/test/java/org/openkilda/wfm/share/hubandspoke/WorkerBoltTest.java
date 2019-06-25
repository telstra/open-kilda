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

package org.openkilda.wfm.share.hubandspoke;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.TupleImpl;
import org.apache.storm.tuple.Values;
import org.apache.storm.utils.Utils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

@RunWith(MockitoJUnitRunner.class)
public class WorkerBoltTest {
    private static final String HUB_COMPONENT = "hub";
    private static final String SPOUT_COMPONENT = "spout";
    private static final String COORDINATOR_COMPONENT = CoordinatorBolt.ID;

    private static final String WORKER_TO_HUB_STREAM_ID = "worker-to-hub";

    private static final String FIELD_ID_KEY = MessageKafkaTranslator.FIELD_ID_KEY;
    private static final String FIELD_ID_PAYLOAD = "payload";
    private static final String FIELD_ID_CONTEXT = AbstractBolt.FIELD_ID_CONTEXT;

    private static final Fields STREAM_FIELDS = new Fields(FIELD_ID_KEY, FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT);

    private static final int HUB_TASK_ID = 1;
    private static final int SPOUT_TASK_ID = 2;
    private static final int COORDINATOR_TASK_ID = 3;

    private WorkerDummyImpl worker;

    @Mock
    private OutputCollector output;

    @Mock
    private TopologyContext topologyContext;

    @Before
    public void setUp() {
        WorkerBolt.Config config = WorkerBolt.Config.builder()
                .hubComponent(HUB_COMPONENT)
                .workerSpoutComponent(SPOUT_COMPONENT)
                .streamToHub(WORKER_TO_HUB_STREAM_ID)
                .build();
        worker = new WorkerDummyImpl(config);

        when(topologyContext.getComponentId(HUB_TASK_ID)).thenReturn(HUB_COMPONENT);
        when(topologyContext.getComponentId(SPOUT_TASK_ID)).thenReturn(SPOUT_COMPONENT);
        when(topologyContext.getComponentId(COORDINATOR_TASK_ID)).thenReturn(COORDINATOR_COMPONENT);

        when(topologyContext.getComponentOutputFields(HUB_COMPONENT, Utils.DEFAULT_STREAM_ID))
                .thenReturn(STREAM_FIELDS);
        when(topologyContext.getComponentOutputFields(SPOUT_COMPONENT, Utils.DEFAULT_STREAM_ID))
                .thenReturn(STREAM_FIELDS);
        when(topologyContext.getComponentOutputFields(COORDINATOR_COMPONENT, Utils.DEFAULT_STREAM_ID))
                .thenReturn(STREAM_FIELDS);

        worker.prepare(Collections.emptyMap(), topologyContext, output);
    }

    @Test
    public void ensureConfigDefaultValues() {
        WorkerBolt.Config config = WorkerBolt.Config.builder()
                .streamToHub("hub-stream")
                .hubComponent("hub")
                .workerSpoutComponent("spout")
                .build();

        Assert.assertTrue(config.isAutoAck());
        Assert.assertEquals(config.getDefaultTimeout(), 100);
    }

    @Test
    public void cancelledTimeout() throws Exception {
        String key = "key";
        String payload = "payload";

        Tuple request = new TupleImpl(topologyContext, new Values(key, payload, new CommandContext()), HUB_TASK_ID,
                                      Utils.DEFAULT_STREAM_ID);
        worker.execute(request);

        Tuple response = new TupleImpl(topologyContext, new Values(key, payload, new CommandContext()), SPOUT_TASK_ID,
                                       Utils.DEFAULT_STREAM_ID);
        worker.execute(response);

        Tuple timeout = new TupleImpl(topologyContext, new Values(key, payload, new CommandContext()),
                                      COORDINATOR_TASK_ID, Utils.DEFAULT_STREAM_ID);
        worker.execute(timeout);

        Assert.assertNull("Must not produce unhandled input errors", worker.lastError);
    }

    @Test
    public void timeoutClearPendingTasks() throws Exception {
        String key = "key";
        String payload = "payload";

        Tuple request = new TupleImpl(topologyContext, new Values(key, payload, new CommandContext()), HUB_TASK_ID,
                                      Utils.DEFAULT_STREAM_ID);
        worker.execute(request);

        Tuple timeout = new TupleImpl(topologyContext, new Values(key, payload, new CommandContext()),
                                      COORDINATOR_TASK_ID, Utils.DEFAULT_STREAM_ID);
        worker.execute(timeout);
        reset(output);

        Tuple response = new TupleImpl(topologyContext, new Values(key, payload, new CommandContext()), SPOUT_TASK_ID,
                                       Utils.DEFAULT_STREAM_ID);
        worker.execute(response);

        // if timeout have not cleaned pending request, our dummy will try to pass response to the HUB.
        verify(output).ack(response);
        verifyNoMoreInteractions(output);
    }

    @Test
    public void multipleRequestsArePossible() {
        String key = "key";
        String payload = "payload";

        Tuple request = new TupleImpl(topologyContext, new Values(key, payload, new CommandContext()), HUB_TASK_ID,
                                      Utils.DEFAULT_STREAM_ID);
        worker.execute(request);

        worker.emitHubResponse = false;
        reset(output);
        Tuple response = new TupleImpl(topologyContext, new Values(key, payload, new CommandContext()), SPOUT_TASK_ID,
                                       Utils.DEFAULT_STREAM_ID);
        worker.execute(response);
        verify(output).ack(response);
        verifyNoMoreInteractions(output);

        reset(output);
        worker.emitHubResponse = true;
        worker.execute(response);
        // cancel timeout
        verify(output).emit(eq(CoordinatorBolt.INCOME_STREAM), eq(response), Mockito.any());
        // hub response
        verify(output).emitDirect(eq(HUB_TASK_ID), eq(WORKER_TO_HUB_STREAM_ID), eq(response), Mockito.any());
        verify(output).ack(response);
        verifyNoMoreInteractions(output);
    }

    private static final class WorkerDummyImpl extends WorkerBolt {
        private boolean emitHubResponse = true;
        private Exception lastError;

        private WorkerDummyImpl(Config config) {
            super(config);
        }

        @Override
        protected void dispatch(Tuple input) throws Exception {
            try {
                super.dispatch(input);
            } catch (Exception e) {
                lastError = e;
                throw e;
            }
        }

        @Override
        protected void onHubRequest(Tuple input) throws Exception {
            // dummy implementation - do nothing
        }

        @Override
        protected void onAsyncResponse(Tuple request, Tuple response) throws Exception {
            if (emitHubResponse) {
                emitResponseToHub(request, new Values("worker"));
            }
        }

        @Override
        protected void onRequestTimeout(Tuple request) {
            // dummy implementation - do nothing
        }

        @Override
        protected void unhandledInput(Tuple input) {
            throw new IllegalStateException(String.format("Unhandled input %s", input));
        }
    }
}
