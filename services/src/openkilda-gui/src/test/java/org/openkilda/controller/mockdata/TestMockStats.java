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

package org.openkilda.controller.mockdata;

import java.text.SimpleDateFormat;
import java.util.Date;

public final class TestMockStats {

    private TestMockStats() {

    }
    
    public static final String FLOW_ID = "ff";

    public static final String START_DATE = newDate(new Date());

    public static final String END_DATE = newDate(new Date(new Date().getTime() - 24 * 60 * 60
            * 1000));

    public static final String METRIC_BITS = "bits";

    public static final String METRIC_BYTES = "bytes";

    public static final String METRIC_PACKETS = "packets";

    public static final String DIRECTION_FORWARD = "forward";

    public static final String DIRECTION_REVERSE = "reverse";

    public static final String DIRECTION_BOTH = "null";

    public static final String DOWNSAMPLE = "10m";

    /**
     * Gets the date format.
     *
     * @return
     */
    public static String newDate(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-hh:mm:ss");
        String strDate = formatter.format(date);
        return strDate;
    }
}
