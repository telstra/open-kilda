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
import org.openkilda.wfm.error.PipelineException;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;
import java.util.Map;

public abstract class AbstractBolt extends BaseRichBolt {
    public static final String FIELD_ID_CONTEXT = "context";

    protected transient Logger log = makeLog();
    private transient OutputCollector output;

    @Override
    public void execute(Tuple input) {
        log.debug(
                "{} input tuple from {}:{} size {}",
                getClass().getName(), input.getSourceComponent(), input.getSourceStreamId(), input.size());
        try {
            handleInput(input);
        } catch (Exception e) {
            log.error(String.format("Unhandled exception in %s", getClass().getName()), e);
        } finally {
            output.ack(input);
        }
    }

    void emit(Tuple anchor, List<Object> payload) {
        log.debug("emit tuple into default stream: {}", payload);
        output.emit(anchor, payload);
    }

    void emit(String stream, Tuple anchor, List<Object> payload) {
        log.debug("emit tuple into {} stream: {}", stream, payload);
        output.emit(stream, anchor, payload);
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

        init();
    }

    protected void init() { }

    protected CommandContext pullContext(Tuple input) throws PipelineException {
        CommandContext value;
        try {
            Object raw = input.getValueByField(FIELD_ID_CONTEXT);
            if (raw instanceof String) {
                value = new CommandContext((String) raw);
            } else {
                value = (CommandContext) raw;
            }
        } catch (ClassCastException e) {
            throw new PipelineException(this, input, FIELD_ID_CONTEXT, e.toString());
        }
        return value;
    }

    protected <T> T pullValue(Tuple input, String field, Class<T> klass) throws PipelineException {
        T value;
        try {
            value = klass.cast(input.getValueByField(field));
        } catch (ClassCastException e) {
            throw new PipelineException(this, input, field, e.toString());
        }
        return value;
    }

    protected OutputCollector getOutput() {
        return output;
    }

    private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
        stream.defaultReadObject();

        log = makeLog();
    }

    private Logger makeLog() {
        return LoggerFactory.getLogger(getClass());
    }
}
