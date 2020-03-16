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

package org.openkilda.utility;

import java.util.Collection;
import java.util.Iterator;

public final class CollectionUtil {

    private CollectionUtil() {
    }

    /**
     * Checks if is collection empty.
     *
     * @param collection the collection
     * @return true, if is collection empty
     */
    public static boolean isEmpty(final Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * Concatenate all items present in the collection separated by the given
     * separator. .
     *
     * @param items the list of items
     * @param separator the separator
     * @return the string
     */
    public static String toString(final Collection<String> items, final String separator) {
        if (isEmpty(items)) {
            return null;
        }

        StringBuilder result = new StringBuilder();
        Iterator<String> iterator = items.iterator();

        while (iterator.hasNext()) {
            result.append(iterator.next());
            if (iterator.hasNext()) {
                result.append(separator);
            }
        }
        return result.toString();
    }
}
