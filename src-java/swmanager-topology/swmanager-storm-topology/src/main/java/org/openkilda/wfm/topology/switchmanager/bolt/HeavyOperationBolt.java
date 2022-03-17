/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.switchmanager.bolt;

import static org.openkilda.wfm.topology.switchmanager.bolt.SwitchManagerHub.FIELD_ID_COOKIE;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.MessageCookie;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.RuleManagerImpl;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.switchmanager.model.SwitchEntities;
import org.openkilda.wfm.topology.switchmanager.service.ValidationService;
import org.openkilda.wfm.topology.switchmanager.service.impl.ValidationServiceImpl;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class HeavyOperationBolt extends AbstractBolt {
    public static final String ID = "heavy.operation.bolt";
    public static final String INCOME_STREAM = "heavy.operation.stream";

    private final RuleManagerConfig ruleManagerConfig;
    private transient ValidationService validationService;

    public HeavyOperationBolt(PersistenceManager persistenceManager, RuleManagerConfig ruleManagerConfig) {
        super(persistenceManager);
        this.ruleManagerConfig = ruleManagerConfig;
    }

    @Override
    protected void init() {
        super.init();
        validationService = new ValidationServiceImpl(persistenceManager, new RuleManagerImpl(ruleManagerConfig));
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        if (SwitchManagerHub.ID.equals(input.getSourceComponent())) {
            handleBuildExpectedEntities(input);
        } else {
            unhandledInput(input);
        }
    }

    private void handleBuildExpectedEntities(Tuple input) throws PipelineException {
        SwitchId switchId = pullValue(input, MessageKafkaTranslator.FIELD_ID_PAYLOAD, SwitchId.class);
        log.info("Received build expected entities request for switch {}", switchId);
        MessageCookie messageCookie = pullValue(input, FIELD_ID_COOKIE, MessageCookie.class);
        Message message;
        try {
            SwitchEntities expectedEntities = new SwitchEntities(validationService.buildExpectedEntities(switchId));
            message = new InfoMessage(expectedEntities, getCommandContext().getCorrelationId(), messageCookie);
        } catch (Exception e) {
            ErrorData errorData = new ErrorData(
                    ErrorType.INTERNAL_ERROR, "Enable to build expected switch entities.", e.getMessage());
            message = new ErrorMessage(errorData, getCommandContext().getCorrelationId(), messageCookie);
        }

        Values values = new Values(switchId, message, getCommandContext());
        getOutput().emitDirect(input.getSourceTask(), SwitchManagerHub.INCOME_STREAM, input, values);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declareStream(SwitchManagerHub.INCOME_STREAM, true, MessageKafkaTranslator.STREAM_FIELDS);
    }
}
