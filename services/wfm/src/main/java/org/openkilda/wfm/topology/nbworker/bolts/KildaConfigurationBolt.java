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

package org.openkilda.wfm.topology.nbworker.bolts;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.model.system.KildaConfigurationDto;
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.messaging.nbtopology.request.KildaConfigurationGetRequest;
import org.openkilda.messaging.nbtopology.request.KildaConfigurationUpdateRequest;
import org.openkilda.messaging.nbtopology.response.KildaConfigurationResponse;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.mappers.KildaConfigurationMapper;
import org.openkilda.wfm.topology.nbworker.services.KildaConfigurationService;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;

import java.util.Collections;
import java.util.List;

public class KildaConfigurationBolt extends PersistenceOperationsBolt {
    private transient KildaConfigurationService kildaConfigurationService;

    public KildaConfigurationBolt(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void init() {
        kildaConfigurationService = new KildaConfigurationService(repositoryFactory, transactionManager);
    }

    @Override
    List<InfoData> processRequest(Tuple tuple, BaseRequest request, String correlationId) {
        KildaConfigurationDto kildaConfigurationDto = null;
        if (request instanceof KildaConfigurationGetRequest) {
            kildaConfigurationDto = getKildaConfiguration();
        } else if (request instanceof KildaConfigurationUpdateRequest) {
            kildaConfigurationDto = updateKildaConfiguration(
                    ((KildaConfigurationUpdateRequest) request).getKildaConfigurationDto());
        } else {
            unhandledInput(tuple);
        }

        return Collections.singletonList(new KildaConfigurationResponse(kildaConfigurationDto));
    }

    private KildaConfigurationDto getKildaConfiguration() {
        return KildaConfigurationMapper.INSTANCE.map(kildaConfigurationService.getKildaConfiguration());
    }

    private KildaConfigurationDto updateKildaConfiguration(KildaConfigurationDto kildaConfigurationDto) {
        try {
            return KildaConfigurationMapper.INSTANCE.map(kildaConfigurationService
                    .updateKildaConfiguration(KildaConfigurationMapper.INSTANCE.map(kildaConfigurationDto)));
        } catch (IllegalArgumentException e) {
            throw new MessageException(ErrorType.PARAMETERS_INVALID, e.getMessage(), "Update kilda configuration.");
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declare(
                new Fields(ResponseSplitterBolt.FIELD_ID_RESPONSE, ResponseSplitterBolt.FIELD_ID_CORELLATION_ID));
    }
}
