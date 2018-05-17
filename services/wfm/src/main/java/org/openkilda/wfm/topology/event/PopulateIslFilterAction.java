package org.openkilda.wfm.topology.event;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.discovery.DiscoveryFilterEntity;
import org.openkilda.messaging.command.discovery.DiscoveryFilterPopulateData;
import org.openkilda.wfm.AbstractAction;
import org.openkilda.wfm.IKildaBolt;
import org.openkilda.wfm.error.MessageFormatException;
import org.openkilda.wfm.error.UnsupportedActionException;
import org.openkilda.wfm.isl.DummyIIslFilter;
import org.openkilda.wfm.protocol.KafkaMessage;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PopulateIslFilterAction extends AbstractAction {
    private final Logger logger = LoggerFactory.getLogger(PopulateIslFilterAction.class);
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

        logger.info("Clean ISL filter");
        filter.clear();
        for (DiscoveryFilterEntity entity : payload.getFilter()) {
            logger.info("Add ISL filter record - switcID=\"{}\" portId=\"{}\"", entity.switchId, entity.portId);
            filter.add(entity.switchId, entity.portId);
        }
    }

    @Override
    protected Boolean handleError(Exception e) {
        boolean isHandled = true;

        try {
            throw e;
        } catch (MessageFormatException exc) {
            logger.error("Can\'t unpack input tuple: {}", exc.getCause().getMessage());

            for (int i = 0; i < getTuple().size(); i++) {
                logger.error("Field #{}: {}", i, getTuple().getValue(i));
            }
        } catch (Exception exc) {
            isHandled = false;
        }

        return isHandled;
    }
}
