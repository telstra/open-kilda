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

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.FeatureTogglesUpdate;
import org.openkilda.messaging.model.system.FeatureTogglesDto;
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.messaging.nbtopology.request.CreateOrUpdateFeatureTogglesRequest;
import org.openkilda.messaging.nbtopology.request.GetFeatureTogglesRequest;
import org.openkilda.messaging.nbtopology.response.FeatureTogglesResponse;
import org.openkilda.model.FeatureToggles;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.error.FeatureTogglesNotFoundException;
import org.openkilda.wfm.share.mappers.FeatureTogglesMapper;
import org.openkilda.wfm.topology.nbworker.StreamType;
import org.openkilda.wfm.topology.nbworker.services.FeatureTogglesService;
import org.openkilda.wfm.topology.nbworker.services.IFeatureTogglesCarrier;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FeatureTogglesBolt extends PersistenceOperationsBolt implements IFeatureTogglesCarrier {
    public static final String STREAM_NOTIFICATION_ID = StreamType.NOTIFICATION.toString();
    public static final Fields STREAM_NOTIFICATION_FIELDS = new Fields(
            DiscoveryEncoderBolt.FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT);

    private transient FeatureTogglesService featureTogglesService;

    public FeatureTogglesBolt(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        super.prepare(stormConf, context, collector);
        featureTogglesService = new FeatureTogglesService(this, repositoryFactory, transactionManager);
    }

    @Override
    List<InfoData> processRequest(Tuple tuple, BaseRequest request) {
        FeatureTogglesDto featureTogglesDto = null;
        if (request instanceof GetFeatureTogglesRequest) {
            featureTogglesDto = getFeatureToggles();
        } else if (request instanceof CreateOrUpdateFeatureTogglesRequest) {
            featureTogglesDto = createOrUpdateFeatureToggles(
                    ((CreateOrUpdateFeatureTogglesRequest) request).getFeatureTogglesDto());
        }

        return Collections.singletonList(new FeatureTogglesResponse(featureTogglesDto));
    }

    private FeatureTogglesDto getFeatureToggles() {
        try {
            return FeatureTogglesMapper.INSTANCE.map(featureTogglesService.getFeatureToggles());
        } catch (FeatureTogglesNotFoundException e) {
            throw new MessageException(ErrorType.NOT_FOUND, e.getMessage(), "Feature toggles do not exist.");
        }
    }

    private FeatureTogglesDto createOrUpdateFeatureToggles(FeatureTogglesDto featureTogglesDto) {
        return FeatureTogglesMapper.INSTANCE.map(featureTogglesService
                .createOrUpdateFeatureToggles(FeatureTogglesMapper.INSTANCE.map(featureTogglesDto)));
    }

    // -- carrier --

    @Override
    public void featureTogglesUpdateNotification(FeatureToggles toggles) {
        FeatureTogglesUpdate payload = new FeatureTogglesUpdate(FeatureTogglesMapper.INSTANCE.map(toggles));
        emit(STREAM_NOTIFICATION_ID, getCurrentTuple(), makeNotificationTuple(payload));
    }

    // -- private --

    private Values makeNotificationTuple(InfoData payload) {
        return new Values(payload, getCommandContext());
    }

    // -- storm API --

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declareStream(STREAM_NOTIFICATION_ID, STREAM_NOTIFICATION_FIELDS);
    }
}
