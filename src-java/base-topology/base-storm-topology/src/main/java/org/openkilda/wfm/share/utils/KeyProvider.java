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

package org.openkilda.wfm.share.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.UUID;

public final class KeyProvider {

    private static final String SEPARATOR = " : ";

    private KeyProvider() {}

    public static String generateKey() {
        return UUID.randomUUID().toString();
    }

    public static String generateChainedKey(String parentKey) {
        return joinKeys(generateKey(), parentKey);
    }

    public static String joinKeys(String childKey, String parentKey) {
        return StringUtils.joinWith(SEPARATOR, childKey, parentKey);
    }

    /**
     * Get parent key.
     */
    public static String getParentKey(String key) {
        if (key.contains(SEPARATOR)) {
            return key.substring(key.indexOf(SEPARATOR) + SEPARATOR.length());
        }
        return key;
    }

    /**
     * Get child key.
     */
    public static String getChildKey(String key) {
        if (key.contains(SEPARATOR)) {
            return key.substring(0, key.indexOf(SEPARATOR));
        }
        return key;
    }
}
