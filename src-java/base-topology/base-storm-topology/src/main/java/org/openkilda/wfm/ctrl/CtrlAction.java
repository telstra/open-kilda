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

package org.openkilda.wfm.ctrl;

import org.openkilda.messaging.ctrl.RequestData;
import org.openkilda.wfm.AbstractAction;
import org.openkilda.wfm.error.MessageFormatException;
import org.openkilda.wfm.error.UnsupportedActionException;
import org.openkilda.wfm.topology.AbstractTopology;

import org.apache.storm.tuple.Tuple;

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
            case "clearState":
                action = new ClearStateAction(this, message);
                break;
            case "dumpBySwitch":
                action = new DumpBySwitchStateAction(this, message);
                break;
            case "dumpResorceCache":
                action = new DumpByResorceCacheAction(this, message);
                break;
            default:
                throw new UnsupportedActionException(payload.getAction());
        }

        action.run();
    }

    @Override
    protected void commit() {
        if (! isHandled) {
            return;
        }
        super.commit();
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


    public static boolean boltHandlerEntrance(ICtrlBolt bolt, Tuple tuple) {
        CtrlAction ctrl = new CtrlAction(bolt, tuple);
        ctrl.run();
        return ctrl.getHandled();
    }
}
