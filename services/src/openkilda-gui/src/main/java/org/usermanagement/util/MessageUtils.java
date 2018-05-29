package org.usermanagement.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;


/**
 * The Class MessageUtil.
 */
@Component
@PropertySource("classpath:message.properties")
public class MessageUtils {

    @Value("${attribute.unique.message}")
    private String attributeUnique;

    /** The attribute not null. */
    @Value("${attribute.not.null.message}")
    private String attributeNotNull;

    @Value("${attribute.invalid.message}")
    private String attributeInvalid;

    /** The attribute not found. */
    @Value("${attribute.not.found.message}")
    private String attributeNotFound;

    @Value("${attribute.deletion.not.allowed.message}")
    private String attributeDeletionNotAllowed;

    @Value("${attribute.password.invalid}")
    private String attributePasswordInvalid;

    @Value("${attribute.2fa.not.configured}")
    private String attribute2faNotConfiured;

    /**
     * Gets the attribute unique.
     *
     * @param attribute the attribute
     * @return the attribute unique
     */
    public String getAttributeUnique(final String attribute) {
        return attributeUnique.replace("{{ATTRIBUTE}}", attribute);
    }

    /**
     * Gets the attribute not null.
     *
     * @param attribute the attribute
     * @return the attribute not null
     */
    public String getAttributeNotNull(final String attribute) {
        return attributeNotNull.replace("{{ATTRIBUTE}}", attribute);
    }

    /**
     * Gets the attribute invalid.
     *
     * @param attribute the attribute
     * @param value the value
     * @return the attribute invalid
     */
    public String getAttributeInvalid(final String attribute, final String value) {
        return attributeInvalid.replace("{{ATTRIBUTE}}", attribute).replace("{{VALUE}}", value);
    }

    /**
     * Gets the attribute not found.
     *
     * @param attribute the attribute
     * @return the attribute not found
     */
    public String getAttributeNotFound(String attribute) {
        return attributeNotFound.replace("{{ATTRIBUTE}}", attribute);
    }

    /**
     * Gets the attribute deletion not allowed.
     *
     * @param attribute1 the attribute 1
     * @param attribute2 the attribute 2
     * @return the attribute deletion not allowed
     */
    public String getAttributeDeletionNotAllowed(String attribute1, String attribute2) {
        return attributeDeletionNotAllowed.replace("{{ATTRIBUTE1}}", attribute1).replace(
                "{{ATTRIBUTE2}}", attribute2);
    }

    /**
     * Gets the attribute password invalid.
     *
     * @return the attribute password invalid
     */
    public String getAttributePasswordInvalid() {
        return attributePasswordInvalid;
    }

    /**
     * Gets the attribute 2 fa not confiured.
     *
     * @return the attribute 2 fa not confiured
     */
    public String getAttribute2faNotConfiured() {
        return attribute2faNotConfiured;
    }
}
