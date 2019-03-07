/* Copyright 2019 Telstra Open Source
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

package org.openkilda.testing;

import org.openkilda.messaging.model.grpc.OnOffState;

public final class ConstantsGrpc {
    public static final String REMOTE_LOG_IP = "1.1.11.111";
    public static final Integer REMOTE_LOG_PORT = 10514;
    public static final OnOffState DEFAULT_LOG_MESSAGES_STATE = OnOffState.OFF;
    public static final OnOffState DEFAULT_LOG_OF_MESSAGES_STATE = OnOffState.OFF;

    private ConstantsGrpc() {
        throw new UnsupportedOperationException();
    }
}
