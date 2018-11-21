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

package org.openkilda.testing.service.elastic.model;

public final class KildaTypes {
    public static final String NORTHBOUND = "kilda-NB";
    public static final String FLOODLIGHT = "kilda-FL";
    public static final String STORM = "kilda-storm";
    public static final String TOPOLOGY_ENGINE = "kilda-TE";

    private KildaTypes() {
        throw new UnsupportedOperationException();
    }

}
