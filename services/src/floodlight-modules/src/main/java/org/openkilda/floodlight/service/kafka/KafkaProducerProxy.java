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

package org.openkilda.floodlight.service.kafka;

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.messaging.Message;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;

public class KafkaProducerProxy implements IKafkaProducerService {
    private final KafkaChannel owner;

    private IKafkaProducerService target;

    public KafkaProducerProxy(KafkaChannel owner) {
        this.owner = owner;
    }

    public IKafkaProducerService getTarget() {
        return target;
    }

    @Override
    public void enableGuaranteedOrder(String topic) {
        target.enableGuaranteedOrder(topic);
    }

    @Override
    public void disableGuaranteedOrder(String topic) {
        target.disableGuaranteedOrder(topic);
    }

    @Override
    public void disableGuaranteedOrder(String topic, long transitionPeriod) {
        target.disableGuaranteedOrder(topic, transitionPeriod);
    }

    @Override
    public void sendMessageAndTrack(String topic, Message message) {
        target.sendMessageAndTrack(topic, message);
    }

    @Override
    public SendStatus sendMessage(String topic, Message message) {
        return target.sendMessage(topic, message);
    }

    @Override
    public void setup(FloodlightModuleContext moduleContext) throws FloodlightModuleException {
        if (!owner.getConfig().isTestingMode()) {
            target = new KafkaProducerService();
        } else {
            target = new TestAwareKafkaProducerService();
        }

        target.setup(moduleContext);
    }
}
