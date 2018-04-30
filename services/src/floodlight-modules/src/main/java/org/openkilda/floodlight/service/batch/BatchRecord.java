/*
 * Copyright 2017 Telstra Open Source
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openkilda.floodlight.service.batch;

import org.openkilda.floodlight.SwitchUtils;
import org.openkilda.floodlight.switchmanager.OFInstallException;

import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

class BatchRecord {
    private static final Logger log = LoggerFactory.getLogger(BatchRecord.class);

    private final SwitchUtils switchUtils;

    private final List<OFPendingMessage> batch;
    private final LinkedList<OFPendingMessage> barriers;
    private boolean errors = false;
    private boolean complete = false;

    BatchRecord(SwitchUtils switchUtils, List<OFPendingMessage> batch) {
        this.switchUtils = switchUtils;
        this.batch = batch;
        this.barriers = new LinkedList<>();
    }

    void write() throws OFInstallException {
        Map<DatapathId, IOFSwitch> affectedSwitches = writePayload();
        writeBarriers(affectedSwitches);

        complete = 0 == barriers.size();

        log.debug("Write {}(+{}) messages", batch.size(), barriers.size());
    }

    boolean handleResponse(OFMessage response) {
        boolean match = true;

        if (recordResponse(barriers, response)) {
            log.debug("Have barrier message response");
            updateBarriers();
        } else if (recordResponse(batch, response)) {
            log.debug(
                    "Have response for some of payload messages (xId: {}, type: {})",
                    response.getXid(), response.getType());
            errors = OFType.ERROR == response.getType();
        } else {
            match = false;
        }

        return match;
    }

    private Map<DatapathId, IOFSwitch> writePayload() throws OFInstallException {
        HashMap<DatapathId, IOFSwitch> affectedSwitches = new HashMap<>();

        for (OFPendingMessage record : batch) {
            DatapathId dpId = record.getDpId();
            IOFSwitch sw = affectedSwitches.get(dpId);
            if (sw == null) {
                sw = switchUtils.lookupSwitch(dpId);
                affectedSwitches.put(dpId, sw);
            }

            if (!sw.write(record.getRequest())) {
                throw new OFInstallException(dpId, record.getRequest());
            }
        }

        return affectedSwitches;
    }

    private void writeBarriers(Map<DatapathId, IOFSwitch> affectedSwitches) throws OFInstallException {
        for (Map.Entry<DatapathId, IOFSwitch> keyValue : affectedSwitches.entrySet()) {
            DatapathId dpId = keyValue.getKey();
            IOFSwitch sw = keyValue.getValue();

            OFPendingMessage barrierRecord = new OFPendingMessage(dpId, sw.getOFFactory().barrierRequest());
            if (!sw.write(barrierRecord.getRequest())) {
                throw new OFInstallException(dpId, barrierRecord.getRequest());
            }
            barriers.addLast(barrierRecord);
        }
    }

    private boolean recordResponse(List<OFPendingMessage> pending, OFMessage response) {
        long xid = response.getXid();
        for (OFPendingMessage record : pending) {
            if (record.getXid() != xid) {
                continue;
            }

            record.setResponse(response);

            return true;
        }

        return false;
    }

    private void updateBarriers() {
        boolean allDone = true;

        for (OFPendingMessage record : barriers) {
            if (record.isPending()) {
                allDone = false;
                break;
            }
        }

        if (allDone) {
            removePendingState();
            complete = true;
        }
    }

    private void removePendingState() {
        for (OFPendingMessage record : batch) {
            if (record.isPending()) {
                record.setResponse(null);
            }
        }
    }

    boolean isComplete() {
        return complete;
    }

    boolean isErrors() {
        return errors;
    }

    List<OFPendingMessage> getBatch() {
        return batch;
    }
}
