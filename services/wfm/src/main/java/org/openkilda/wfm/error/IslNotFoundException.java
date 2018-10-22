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

public class IslNotFoundException extends AbstractException {
    public IslNotFoundException(String sourceSwitch, Integer sourcePort,
                                String destinationSwitch, Integer destinationPort) {
        super(String.format("ISL with following parameters is not found: "
                          + "source switch '%s', source port '%d', destination switch '%s', destination port '%d'.",
                sourceSwitch, sourcePort, destinationSwitch, destinationPort));
    }
}
