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

import org.openkilda.floodlight.service.IService;
import org.openkilda.messaging.Message;

public interface IKafkaProducerService extends IService {
    void enableGuaranteedOrder(String topic);

    void disableGuaranteedOrder(String topic);

    void disableGuaranteedOrder(String topic, long transitionPeriod);

    void sendMessageAndTrack(String topic, Message message);

    void sendMessageAndTrack(String topic, String key, Message message);

    SendStatus sendMessage(String topic, Message message);

    int getFailedSendMessageCounter();
}
