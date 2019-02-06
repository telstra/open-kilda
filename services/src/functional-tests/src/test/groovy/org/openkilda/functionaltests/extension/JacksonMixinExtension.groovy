package org.openkilda.functionaltests.extension

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

/**
 * This adds convenience methods (syntactic sugar) to operations related to serialization/deserialization.
 * Methods should not be called directly from this class, instead they are added as extension methods to corresponding
 * delegate classes by Groovy extension mechanism.<br/>
 * Example: <code> '{"key": "value"}'.to(Map) == [key: "value"] </code><br/>
 * Example: <code>[key: "value"].toJson() == '{"key": "value"}'</code>
 */
class JacksonMixinExtension {
    static ObjectMapper mapper = initMapper()

    static private def initMapper() {
        new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    /**
     * Serialize certain object(usually instance of POGO/POJO class) to a json
     *
     * @param delegate
     * @return
     */
    static String toJson(Object delegate) {
        mapper.writeValueAsString(delegate)
    }

    /**
     * Make json string look pretty (add indents, etc.). Return itself if given string is not a json
     *
     * @param delegate
     * @return
     */
    static String prettyJson(String delegate) {
        try {
            def obj = delegate.to(Object)
            return obj.toJson()
        } catch (e) {
            return delegate
        }
    }

    /**
     * Take only first N chars from the string, truncate the rest.
     *
     * @param delegate
     * @return
     */
    static String truncate(String delegate, int maxChars = 2000) {
        def result = delegate.take(maxChars)
        if (delegate.size() > maxChars) {
            result += "...(truncated)"
        }
        return result
    }

    /**
     * Convert certain Map to a pojo class
     *
     * @param delegate
     * @param clazz
     * @return
     */
    static <T> T to(Map delegate, Class<T> clazz) {
        mapper.convertValue(delegate, clazz)
    }

    /**
     * Deserialize certain json string to a pojo class
     *
     * @param delegate
     * @param clazz
     * @return
     */
    static <T> T to(String delegate, Class<T> clazz) {
        mapper.readValue(delegate, clazz)
    }

    /**
     * Deserialize certain json StringReader to a pojo class
     *
     * @param delegate
     * @param clazz
     * @return
     */
    static <T> T to(StringReader delegate, Class<T> clazz) {
        to(delegate.text, clazz)
    }
}
