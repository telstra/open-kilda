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

import org.openkilda.applications.model.Exclusion;

/**
 * {@code FlowAlreadyExistException} indicates that a flow already exist.
 */
public class ExclusionAlreadyExistException extends Exception {

    public ExclusionAlreadyExistException(Exclusion exclusion) {
        super(String.format("%s already exists", exclusion));
    }
}
