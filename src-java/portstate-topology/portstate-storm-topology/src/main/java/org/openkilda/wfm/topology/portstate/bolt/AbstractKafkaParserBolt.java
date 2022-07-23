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

package org.openkilda.wfm.topology.portstate.bolt;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.topology.portstate.exceptions.MessageException;

public abstract class AbstractKafkaParserBolt extends AbstractBolt {

    public AbstractKafkaParserBolt() {
    }

    public AbstractKafkaParserBolt(String lifeCycleEventSourceComponent) {
        super(lifeCycleEventSourceComponent);
    }

    protected InfoData getInfoData(Message message) throws MessageException {
        if (!(message instanceof InfoMessage)) {
            throw new MessageException(message.getClass().getName() + " is not an InfoMessage");
        }
        return ((InfoMessage) message).getData();
    }
}
