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

package org.openkilda.wfm.error;

import lombok.Getter;
import org.apache.storm.tuple.Tuple;

@Getter
public class MessageFormatException extends Exception {
    private final Tuple tuple;

    public MessageFormatException(Tuple tuple, Throwable throwable) {
        super("Invalid input message/tuple", throwable);

        this.tuple = tuple;
    }

    // TODO unused, consider to remove
    public MessageFormatException(String s, Throwable throwable) {
        super(s, throwable);
        tuple = null;
    }
}
