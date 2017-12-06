package org.openkilda.wfm.ctrl;

import org.apache.storm.tuple.Tuple;
import org.openkilda.messaging.ctrl.RequestData;
import org.openkilda.wfm.AbstractAction;
import org.openkilda.wfm.UnsupportedActionException;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.MessageFormatException;

public class CtrlAction extends AbstractAction {
    private Boolean isHandled = false;
    private final ICtrlBolt bolt;

    public CtrlAction(ICtrlBolt bolt, Tuple tuple) {
        super(bolt, tuple);
        this.bolt = bolt;
    }

    @Override
    protected void handle()
            throws MessageFormatException, UnsupportedActionException {
        String source = getTuple().getSourceComponent();

        if (! source.equals(AbstractTopology.BOLT_ID_CTRL_ROUTE)) {
            return;
        }

        isHandled = true;

        AbstractAction action;
        RouteMessage message = new RouteMessage(getTuple());
        RequestData payload = message.getPayload();
        switch (payload.getAction()) {
            case "list":
                action = new ListAction(this, message);
                break;
            case "dump":
                action = new DumpStateAction(this, message);
                break;
            default:
                throw new UnsupportedActionException(payload.getAction());
        }

        action.run();
    }

    public Boolean getHandled() {
        return isHandled;
    }

    public String getStreamId() {
        return getBolt().getCtrlStreamId();
    }

    @Override
    public ICtrlBolt getBolt() {
        return bolt;
    }


    public static boolean boltHandlerEntrance(ICtrlBolt bolt, Tuple tuple)
    {
        CtrlAction ctrl = new CtrlAction(bolt, tuple);
        ctrl.run();
        return ctrl.getHandled();
    }
}
