package org.openkilda.wfm.topology.utils;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.wfm.topology.MessageException;

import java.io.IOException;
import java.util.Map;

public abstract class AbstractKafkaParserBolt extends BaseRichBolt {
    protected OutputCollector collector;

    protected String getJson(Tuple tuple) {
        return tuple.getString(0);
    }

    protected Message getMessage(String json) throws IOException {
        return Utils.MAPPER.readValue(json, Message.class);
    }

    protected InfoData getInfoData(Message message) throws MessageException {
        if (!(message instanceof InfoMessage)) {
            throw new MessageException(message.getClass().getName() + " is not an InfoMessage");
        }
        InfoData data = ((InfoMessage) message).getData();
        return data;
    }

    protected InfoData getInfoData(Tuple tuple) throws IOException, MessageException {
        return getInfoData(getMessage(getJson(tuple)));
    }

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.collector = outputCollector;
    }
}
