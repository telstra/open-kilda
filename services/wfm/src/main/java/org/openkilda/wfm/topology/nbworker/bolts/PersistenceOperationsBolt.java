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

import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.IllegalIslStateException;
import org.openkilda.wfm.error.IslNotFoundException;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.nbworker.StreamType;

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

    protected final PersistenceManager persistenceManager;
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

        try {
            List<? extends InfoData> result = processRequest(input, request);
            getOutput().emit(StreamType.RESPONSE.toString(), input, new Values(result, correlationId));
        } catch (IslNotFoundException e) {
            emitErrorMessage(input, ErrorType.NOT_FOUND, e.getMessage(), "Link is not found.");
        } catch (IllegalIslStateException e) {
            emitErrorMessage(input, ErrorType.REQUEST_INVALID, e.getMessage(), "Link is in illegal state.");
        }
    }

    abstract List<? extends InfoData> processRequest(Tuple tuple, BaseRequest request)
            throws IslNotFoundException, IllegalIslStateException;

    private void emitErrorMessage(Tuple input, ErrorType errorType, String errorMessage,
                                  String errorDescription) throws PipelineException {
        getLogger().error("{} {}", errorDescription, errorMessage);
        ErrorData data = new ErrorData(errorType, errorMessage, errorDescription);
        getOutput().emit(StreamType.ERROR.toString(), input,
                new Values(data, pullValue(input, FIELD_ID_CORELLATION_ID, String.class)));
    }

    abstract Logger getLogger();

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(StreamType.RESPONSE.toString(),
                new Fields(ResponseSplitterBolt.FIELD_ID_RESPONSE, ResponseSplitterBolt.FIELD_ID_CORELLATION_ID));
        declarer.declareStream(StreamType.ERROR.toString(),
                new Fields(MessageEncoder.FIELD_ID_PAYLOAD, MessageEncoder.FIELD_ID_CONTEXT));
    }
}
