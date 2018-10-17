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

package org.openkilda.wfm.topology.nbworker.bolts;

import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.nbworker.services.ValidationService;

import com.google.common.collect.ImmutableList;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Map;

public class SwitchValidationsBolt extends AbstractBolt {

    private final PersistenceManager persistenceManager;
    private transient ValidationService validationService;

    public static final String FIELD_ID_CORELLATION_ID = "correlationId";

    public static final String FIELD_ID_REQUEST = "request";

    public SwitchValidationsBolt(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public void init() {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        validationService = new ValidationService(repositoryFactory);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(
                new Fields(ResponseSplitterBolt.FIELD_ID_RESPONSE, ResponseSplitterBolt.FIELD_ID_CORELLATION_ID));
    }

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        SwitchFlowEntries request = pullValue(input, FIELD_ID_REQUEST, SwitchFlowEntries.class);
        final String correlationId = pullValue(input, FIELD_ID_CORELLATION_ID, String.class);

        getOutput().emit(input, new Values(ImmutableList.of(validationService.validate(request)), correlationId));
    }
}
