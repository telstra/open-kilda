/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.flowmonitoring.bolt;

import static org.openkilda.wfm.topology.flowmonitoring.FlowMonitoringTopology.Stream.ISL_UPDATE_STREAM_ID;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.IslBaseLatency;
import org.openkilda.messaging.info.event.IslChangedInfoData;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class IslDataSplitterBolt extends AbstractBolt {

    public static final String ISL_KEY_FIELD = "isl-key";
    public static final String INFO_DATA_FIELD = "info-data";

    public static String getIslKey(SwitchId switchId, int port) {
        return String.format("%s_%s", switchId, port);
    }

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        Message message = pullValue(input, FIELD_ID_PAYLOAD, Message.class);
        InfoData infoData;
        if (message instanceof InfoMessage) {
            infoData = ((InfoMessage) message).getData();
        } else {
            unhandledInput(input);
            return;
        }

        if (infoData instanceof IslChangedInfoData) {
            IslChangedInfoData data = (IslChangedInfoData) infoData;
            emitWithContext(ISL_UPDATE_STREAM_ID.name(),
                    input, new Values(getIslKey(data.getSource().getDatapath(),
                            data.getSource().getPortNumber()), data));
            emitWithContext(ISL_UPDATE_STREAM_ID.name(),
                    input, new Values(getIslKey(data.getDestination().getDatapath(),
                            data.getDestination().getPortNumber()), revert(data)));
        } else if (infoData instanceof IslBaseLatency) {
            IslBaseLatency islBaseLatency = (IslBaseLatency) infoData;
            emit(input, new Values(getIslKey(islBaseLatency.getSrcSwitchId(),
                    islBaseLatency.getSrcPortNo()), infoData, getCommandContext()));
        } else {
            unhandledInput(input);
        }
    }

    private IslChangedInfoData revert(IslChangedInfoData origin) {
        return new IslChangedInfoData(origin.getDestination(), origin.getSource(), origin.isRemoved());
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(ISL_KEY_FIELD, INFO_DATA_FIELD, FIELD_ID_CONTEXT));
        declarer.declareStream(ISL_UPDATE_STREAM_ID.name(),
                new Fields(ISL_KEY_FIELD, INFO_DATA_FIELD, FIELD_ID_CONTEXT));
    }
}
