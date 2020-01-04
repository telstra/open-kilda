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
import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.LATENCY_DATA_FIELD;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.IslOneWayLatency;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.model.IslReference;
import org.openkilda.wfm.topology.isllatency.carriers.OneWayLatencyManipulationCarrier;
import org.openkilda.wfm.topology.isllatency.model.StreamType;
import org.openkilda.wfm.topology.isllatency.service.OneWayLatencyManipulationService;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;


public class OneWayLatencyManipulationBolt extends AbstractBolt implements OneWayLatencyManipulationCarrier {
    private transient OneWayLatencyManipulationService oneWayLatencyManipulationService;

    @Override
    protected void init() {
        oneWayLatencyManipulationService = new OneWayLatencyManipulationService(this);
    }

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        InfoData data = pullValue(input, LATENCY_DATA_FIELD, InfoData.class);

        if (data instanceof IslOneWayLatency) {
            oneWayLatencyManipulationService.handleOneWayLatency((IslOneWayLatency) data);
        } else {
            unhandledInput(input);
        }
    }

    @Override
    public void emitIslOneWayLatency(IslOneWayLatency islOneWayLatency) throws PipelineException {
        IslReference islReference = pullValue(getCurrentTuple(), ISL_GROUPING_FIELD, IslReference.class);
        Values values = new Values(islReference, islOneWayLatency, getCommandContext());
        getOutput().emit(StreamType.LATENCY.toString(), getCurrentTuple(), values);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        Fields oneWayLatencyFields = new Fields(ISL_GROUPING_FIELD, LATENCY_DATA_FIELD, FIELD_ID_CONTEXT);
        declarer.declareStream(StreamType.LATENCY.toString(), oneWayLatencyFields);
    }
}
