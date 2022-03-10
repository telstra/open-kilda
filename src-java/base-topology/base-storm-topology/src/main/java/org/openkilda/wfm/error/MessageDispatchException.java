/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.error;

import org.openkilda.messaging.MessageCookie;

public class MessageDispatchException extends Exception {
    private final MessageCookie cookie;

    public MessageDispatchException(MessageCookie cookie) {
        this.cookie = cookie;
    }

    public MessageDispatchException() {
        this(null);
    }

    @Override
    public String getMessage() {
        return String.format("Unable to dispatch message (unprocessed cookie part: %s)", cookie);
    }

    public String getMessage(MessageCookie rootCookie) {
        return String.format(
                "Unable to dispatch message (unprocessed cookie part: %s, root cookie: %s)", cookie, rootCookie);
    }
}
