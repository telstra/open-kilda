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

package org.openkilda.wfm.topology.nbworker.bolts;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

public abstract class PersistenceOperationsBolt extends AbstractBolt {

    private final PersistenceManager persistenceManager;
    protected transient RepositoryFactory repositoryFactory;

    public static final String FIELD_ID_CORELLATION_ID = "correlationId";

    public static final String FIELD_ID_REQUEST = "request";

    PersistenceOperationsBolt(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        repositoryFactory = persistenceManager.getRepositoryFactory();

        super.prepare(stormConf, context, collector);
    }

    protected void handleInput(Tuple input) throws PipelineException {
        BaseRequest request = pullValue(input, FIELD_ID_REQUEST, BaseRequest.class);
        final String correlationId = pullValue(input, FIELD_ID_CORELLATION_ID, String.class);
        getLogger().debug("Received operation request");

        List<? extends InfoData> result = processRequest(input, request);
        getOutput().emit(input, new Values(result, correlationId));
    }

    abstract List<? extends InfoData> processRequest(Tuple tuple, BaseRequest request);

    abstract Logger getLogger();

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(
                new Fields(ResponseSplitterBolt.FIELD_ID_RESPONSE, ResponseSplitterBolt.FIELD_ID_CORELLATION_ID));
    }
}
