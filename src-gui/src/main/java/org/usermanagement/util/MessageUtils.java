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

    @Value("${attribute.not.null.message}")
    private String attributeNotNull;

    @Value("${attribute.invalid.message}")
    private String attributeInvalid;

    @Value("${attribute.not.valid.message}")
    private String attributeNotvalid;

    /** The attribute not found. */
    @Value("${attribute.not.found.message}")
    private String attributeNotFound;

    @Value("${attribute.deletion.not.allowed.message}")
    private String attributeDeletionNotAllowed;

    @Value("${attribute.password.invalid}")
    private String attributePasswordInvalid;

    @Value("${attribute.2fa.not.configured}")
    private String attribute2faNotConfiured;

    @Value("${attribute.2fa.not.enabled}")
    private String attribute2faNotEnabled;

    @Value("${attribute.password.should.not.same}")
    private String attributePasswordShouldNotSame;

    @Value("${attribute.password.length}")
    private String attributePasswordLength;

    @Value("${unauthorized.message}")
    private String unauthorizedMessage;

    @Value("${attribute.password.must.contain}")
    private String attributePasswordMustContain;

    @Value("${attribute.password.must.not.contain}")
    private String attributePasswordMustNotContain;
    
    @Value("${store.not.configured.message}")
    private String storeMustConfigured;

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
        return attributeDeletionNotAllowed.replace("{{ATTRIBUTE1}}", attribute1).replace("{{ATTRIBUTE2}}", attribute2);
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

    /**
     * Gets the attribute 2 fa not enabled.
     *
     * @return the attribute 2 fa not enabled
     */
    public String getAttribute2faNotEnabled() {
        return attribute2faNotEnabled;
    }

    /**
     * Gets the unauthorized message.
     *
     * @return the unauthorized message
     */
    public String getUnauthorizedMessage() {
        return unauthorizedMessage;
    }

    /**
     * Gets the attribute not valid.
     *
     * @param attribute the attribute
     * @return the attribute invalid
     */
    public String getAttributeNotvalid(final String attribute) {
        return attributeNotvalid.replace("{{ATTRIBUTE}}", attribute);
    }

    /**
     * Gets the attribute password should not same.
     *
     * @return the attribute password should not same
     */
    public String getAttributePasswordShouldNotSame() {
        return attributePasswordShouldNotSame;
    }

    /**
     * Gets the attribute password length.
     *
     * @param min the min
     * @param max the max
     * @return the attribute password length
     */
    public String getAttributePasswordLength(final String min, final String max) {
        return attributePasswordLength.replace("{{VALUE1}}", min).replace("{{VALUE2}}", min);
    }

    /**
     * Gets the attribute password must contain.
     *
     * @return the attribute password must contain
     */
    public String getAttributePasswordMustContain() {
        return attributePasswordMustContain;
    }

    /**
     * Gets the attribute password must not contain.
     *
     * @return the attribute password must not contain
     */
    public String getAttributePasswordMustNotContain() {
        return attributePasswordMustNotContain;
    }
    
    /**
     * Gets the attribute store must be configured.
     *
     * @return store must be configured
     */
    public String getStoreMustConfigured() {
        return storeMustConfigured;
    }
    
    
}
