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

package org.openkilda.wfm.topology.isllatency.bolts;

import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.ISL_GROUPING_FIELD;
import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.ISL_STATUS_FIELD;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.IslStatusUpdateNotification;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.share.model.IslReference;
import org.openkilda.wfm.topology.isllatency.model.StreamType;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;


public class IslStatusUpdateBolt extends AbstractBolt {

    @Override
    protected void handleInput(Tuple tuple) throws PipelineException {
        Message message = pullValue(tuple, FIELD_ID_PAYLOAD, Message.class);

        if (message instanceof InfoMessage) {
            InfoData data = ((InfoMessage) message).getData();

            if (data instanceof IslStatusUpdateNotification) {
                handleIslStatusUpdateNotification(tuple, (IslStatusUpdateNotification) data);
            } else {
                unhandledInput(tuple);
            }
        } else {
            unhandledInput(tuple);
        }
    }

    private void handleIslStatusUpdateNotification(Tuple tuple, IslStatusUpdateNotification notification) {
        IslReference islReference = new IslReference(
                Endpoint.of(notification.getSrcSwitchId(), notification.getSrcPortNo()),
                Endpoint.of(notification.getDstSwitchId(), notification.getDstPortNo()));

        Values values = new Values(islReference, notification, getCommandContext());
        getOutput().emit(StreamType.ISL_STATUS.toString(), tuple, values);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        Fields fields = new Fields(ISL_GROUPING_FIELD, ISL_STATUS_FIELD, FIELD_ID_CONTEXT);
        declarer.declareStream(StreamType.ISL_STATUS.toString(), fields);
    }
}
