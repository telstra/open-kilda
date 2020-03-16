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

package org.usermanagement.service.impl;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

import org.springframework.stereotype.Service;

import org.usermanagement.service.TemplateService;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

@Service
public class VelocityTemplateService implements TemplateService {

    private VelocityEngine velocityEngine;
    private Map<Template, String> templates = new HashMap<TemplateService.Template, String>();

    /**
     * Initialize VelocityEngine.
     */
    @PostConstruct
    private void init() {
        velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        velocityEngine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
        velocityEngine.init();

        templates.put(Template.RESET_ACCOUNT_PASSWORD, "ui/templates/mail/resetAccountPassword.vm");
        templates.put(Template.ACCOUNT_USERNAME, "ui/templates/mail/accountUsername.vm");
        templates.put(Template.ACCOUNT_PASSWORD, "ui/templates/mail/accountPassword.vm");
        templates.put(Template.RESET_2FA, "ui/templates/mail/reset2fa.vm");
        templates.put(Template.CHANGE_PASSWORD, "ui/templates/mail/changePassword.vm");

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.usermanagement.service.TemplateService#mergeTemplateToString(org.
     * usermanagement.service.TemplateService.Template, java.util.Map)
     */
    @Override
    public String mergeTemplateToString(Template template, Map<String, Object> model) {
        org.apache.velocity.Template velocityTemplate = velocityEngine.getTemplate(templates.get(template));
        VelocityContext velocityContext = new VelocityContext(model);
        StringWriter writer = new StringWriter();
        velocityTemplate.merge(velocityContext, writer);
        return writer.toString();
    }
}
