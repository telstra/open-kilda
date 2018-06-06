package org.usermanagement.service;

import java.util.Map;

/**
 * The service for making string from template by the model.
 */
public interface TemplateService {

    /**
     * Take a result string from template and provided model.
     *
     * @param template template.
     * @param model Map with model values.
     * @return result string.
     */
    String mergeTemplateToString(Template template, Map<String, Object> model);

    /**
     * Enum of templates.
     */
    enum Template {
        RESET_ACCOUNT_PASSWORD, ACCOUNT_USERNAME, ACCOUNT_PASSWORD, RESET_2FA, CHANGE_PASSWORD;
    }
}
