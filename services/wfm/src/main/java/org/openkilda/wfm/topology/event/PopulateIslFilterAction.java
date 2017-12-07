package org.openkilda.wfm.topology.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.tuple.Tuple;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.discovery.DiscoveryFilterEntity;
import org.openkilda.messaging.command.discovery.DiscoveryFilterPopulateData;
import org.openkilda.wfm.AbstractAction;
import org.openkilda.wfm.IKildaBolt;
import org.openkilda.wfm.MessageFormatException;
import org.openkilda.wfm.UnsupportedActionException;
import org.openkilda.wfm.isl.DummyIIslFilter;
import org.openkilda.wfm.protocol.KafkaMessage;

public class PopulateIslFilterAction extends AbstractAction {
    private final DummyIIslFilter filter;

    public PopulateIslFilterAction(IKildaBolt bolt, Tuple tuple, DummyIIslFilter filter) {
        super(bolt, tuple);
        this.filter = filter;
    }

    @Override
    protected void handle() throws MessageFormatException, UnsupportedActionException, JsonProcessingException {
        KafkaMessage input = new KafkaMessage(getTuple());
        Message message = input.getPayload();

        if (message.getDestination() != Destination.WFM_OF_DISCOVERY) {
            return;
        }
        if (! (message instanceof CommandMessage)) {
            return;
        }

        CommandMessage command = (CommandMessage) message;
        if (!(command.getData() instanceof DiscoveryFilterPopulateData)) {
            return;
        }

        DiscoveryFilterPopulateData payload = (DiscoveryFilterPopulateData) command.getData();

        filter.clear();
        for (DiscoveryFilterEntity entity : payload.getFilter()) {
            filter.add(entity.switchId, entity.portId);
        }
    }
}
