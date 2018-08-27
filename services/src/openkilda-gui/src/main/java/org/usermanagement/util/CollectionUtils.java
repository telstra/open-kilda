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

package org.usermanagement.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The Class CollectionUtils.
 */
public final class CollectionUtils {

    /**
     * Instantiates a new collection utils.
     */
    private CollectionUtils() {}

    /**
     * Checks if is null or empty.
     *
     * @param collection the collection
     * @return true, if is null or empty
     */
    public static boolean isNullOrEmpty(final Collection<?> collection) {
        return collection == null || collection.size() == 0;
    }
    
    /**
     * Checks if is null or empty.
     *
     * @param map the map
     * @return true, if is null or empty
     */
    public static boolean isNullOrEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    /**
     * Checks if is unique objects.
     *
     * @param <T> the generic type
     * @param collection the collection
     * @return true, if is unique objects
     */
    public static <T> boolean isUniqueObjects(final Collection<T> collection) {
        if (collection instanceof Set) {
            return true;
        }

        Set<T> data = new HashSet<T>();
        data.addAll(collection);

        return data.size() == collection.size();
    }
}
