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

package org.openkilda.wfm.topology.ping.bolt;

import org.openkilda.messaging.model.Ping;
import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.ping.model.PingContext;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.HashSet;

public class Blacklist extends Abstract {
    public static final String BOLT_ID = ComponentId.BLACKLIST.toString();

    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_PING, FIELD_ID_CONTEXT);

    private HashSet<Ping> blacklist;

    @Override
    protected void init() {
        super.init();
        blacklist = new HashSet<>();
    }

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        if (!PingRouter.BOLT_ID.equals(input.getSourceComponent())) {
            unhandledInput(input);
            return;
        }

        String stream = input.getSourceStreamId();
        PingContext pingContext = pullPingContext(input);
        if (PingRouter.STREAM_BLACKLIST_FILTER_ID.equals(stream)) {
            filter(input, pingContext);
        } else if (PingRouter.STREAM_BLACKLIST_UPDATE_ID.equals(stream)) {
            update(pingContext);
        } else {
            unhandledInput(input);
        }
    }

    private void filter(Tuple input, PingContext pingContext) throws PipelineException {
        final Ping ping = pingContext.getPing();

        if (blacklist.contains(ping)) {
            log.debug("{} canceled due to blacklist match", pingContext);
            return;
        }

        Values output = new Values(pingContext, pullContext(input));
        getOutput().emit(input, output);
    }

    private void update(PingContext pingContext) {
        Ping ping = pingContext.getPing();
        if (blacklist.add(ping)) {
            log.info("Add {} into blacklist (error {})", ping, pingContext.getError());
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        outputManager.declare(STREAM_FIELDS);
    }
}
