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

package org.openkilda.floodlight.feature;

import static java.util.regex.Pattern.compile;

import org.openkilda.model.SwitchFeature;

import net.floodlightcontroller.core.IOFSwitch;

import java.util.Optional;
import java.util.regex.Pattern;

public abstract class AbstractFeature {
    protected static final String MANUFACTURER_NICIRA = "Nicira, Inc.";
    protected static final String E_SWITCH_MANUFACTURER_DESCRIPTION = "E";
    protected static final Pattern E_SWITCH_HARDWARE_DESCRIPTION_REGEX = compile("^WB5\\d{3}-E$");
    protected static final Pattern NOVIFLOW_VIRTUAL_SWITCH_HARDWARE_DESCRIPTION_REGEX = compile("^SM5\\d{3}-SM$");
    protected static final String CENTEC_MANUFACTURED = "Centec";
    protected static final Pattern NOVIFLOW_SOFTWARE_DESCRIPTION_REGEX = compile("(.*)NW\\d{3}\\.\\d+\\.\\d+(.*)");
    protected static final String NOVIFLOW_MANUFACTURER_SUFFIX = "noviflow";

    public abstract Optional<SwitchFeature> discover(IOFSwitch sw);
}
