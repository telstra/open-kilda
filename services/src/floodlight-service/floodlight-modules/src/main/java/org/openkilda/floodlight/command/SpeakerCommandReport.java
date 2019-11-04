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

package org.openkilda.floodlight.command;

import lombok.Getter;
import lombok.ToString;

@ToString
public abstract class SpeakerCommandReport {
    @Getter
    protected final SpeakerCommand<?> command;
    protected final Exception error;

    public SpeakerCommandReport(SpeakerCommand<?> command) {
        this(command, null);
    }

    public SpeakerCommandReport(SpeakerCommand<?> command, Exception error) {
        this.command = command;
        this.error = error;
    }

    /**
     * Throw error if command ends with error.
     */
    public void raiseError() throws Exception {
        if (error != null) {
            throw error;
        }
    }
}
