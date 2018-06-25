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
