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

package org.openkilda.wfm;

import org.openkilda.wfm.error.AbstractException;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;

import java.util.Map;

@Slf4j
public abstract class AbstractBolt extends BaseRichBolt {
    private OutputCollector output;

    @Override
    public void execute(Tuple input) {
        try {
            handleInput(input);
        } catch (Exception e) {
            log.error(String.format("Unhandled exception in %s", getClass().getName()), e);
        } finally {
            output.ack(input);
        }
    }

    protected abstract void handleInput(Tuple input) throws AbstractException;

    protected void unhandledInput(Tuple input) {
        log.error(
                "{} is unable to handle input tuple from {} stream {} - have topology being build correctly?",
                getClass().getName(), input.getSourceComponent(), input.getSourceStreamId());
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.output = collector;
    }

    protected OutputCollector getOutput() {
        return output;
    }
}
