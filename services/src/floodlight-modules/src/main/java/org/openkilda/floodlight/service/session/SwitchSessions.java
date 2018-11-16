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

package org.openkilda.floodlight.service.session;

import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFMessage;

import java.util.HashMap;
import java.util.Map;

class SwitchSessions {
    private final Map<Long, Session> sessionsByXid = new HashMap<>();

    Session open(IOFSwitch sw) {
        return new Session(this, sw);
    }

    void handleResponse(OFMessage message) {
        Session session;
        synchronized (sessionsByXid) {
            session = sessionsByXid.get(message.getXid());
        }

        if (session != null && session.handleResponse(message)) {
            unbindSession(session);
        }
    }

    void disconnect() {
        synchronized (sessionsByXid) {
            sessionsByXid.values()
                    // Session can be listed multiple time into sessionsByXid map
                    // so .disconnect() will be called multiple times. Session.disconnect
                    // must be ready to be called multiple times
                    .forEach(Session::disconnect);

            sessionsByXid.clear();
        }
    }

    void bindRequest(Session session, long xid) {
        synchronized (sessionsByXid) {
            sessionsByXid.put(xid, session);
        }
    }

    private void unbindSession(Session session) {
        synchronized (sessionsByXid) {
            for (long xid : session.getAllXids()) {
                sessionsByXid.remove(xid);
            }
        }
    }
}
