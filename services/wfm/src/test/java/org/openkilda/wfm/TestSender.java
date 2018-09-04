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

package org.openkilda.wfm;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.collections4.list.UnmodifiableList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestSender extends Sender {

    private Map<String, List<Object>> sentMessages = new HashMap<>();

    public TestSender() {
        super(null, null);
    }

    @Override
    protected void send(String stream, Object message) throws JsonProcessingException {
        saveMessage(stream, message);
    }

    private void saveMessage(String stream, Object message) {
        sentMessages.computeIfAbsent(stream, k -> new ArrayList<>()).add(message);
    }

    public List<Object> listMessages(String stream) {
        return new UnmodifiableList<>(sentMessages.getOrDefault(stream, Collections.emptyList()));
    }
}
