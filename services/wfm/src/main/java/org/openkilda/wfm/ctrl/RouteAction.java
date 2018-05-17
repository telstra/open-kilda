package org.openkilda.wfm.ctrl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import org.apache.storm.tuple.Tuple;
import org.openkilda.messaging.ctrl.CtrlRequest;
import org.openkilda.wfm.AbstractAction;
import org.openkilda.wfm.IKildaBolt;
import org.openkilda.wfm.error.MessageFormatException;
import org.openkilda.messaging.Message;
import org.openkilda.wfm.protocol.KafkaMessage;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Map;

public class RouteAction extends AbstractAction {
    private String topologyName;
    private Map<String, String> endpoints;

    public RouteAction(
            IKildaBolt bolt, Tuple tuple,
            String topologyName, Map<String, String> endpoint) {
        super(bolt, tuple);

        this.topologyName = topologyName;
        this.endpoints = endpoint;
    }

    @Override
    protected void handle() throws MessageFormatException, JsonProcessingException {
        KafkaMessage input = new KafkaMessage(getTuple());
        Message payload = input.getPayload();

        if (! (payload instanceof CtrlRequest)) {
            getLogger().debug(String.format(
                    "Skip foreign message (correlation-id: %s timestamp: %s)",
                    payload.getCorrelationId(), payload.getTimestamp()));
            return;
        }

        handleMessage((CtrlRequest)payload);
    }

    private void handleMessage(CtrlRequest payload) throws JsonProcessingException {
        RouteMessage message = new RouteMessage(
                payload.getData(), payload.getCorrelationId(), topologyName);
        List<Object> packedMessage = message.pack();

        String glob = payload.getRoute();

        if (Strings.isNullOrEmpty(glob)) {
            glob = "**";
        } else if (glob.equals("*")) {
            glob = "**";
        }

        FileSystem fs = FileSystems.getDefault();
        PathMatcher matcher = fs.getPathMatcher("glob:" + glob);

        for (String bolt : endpoints.keySet()) {
            Path route = fs.getPath(topologyName, bolt);

            if (! matcher.matches(route)) {
                continue;
            }

            getOutputCollector().emit(
                    endpoints.get(bolt), getTuple(), packedMessage);
        }
    }
}
