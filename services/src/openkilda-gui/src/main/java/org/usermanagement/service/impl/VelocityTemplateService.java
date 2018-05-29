package org.usermanagement.service.impl;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.usermanagement.service.TemplateService;

import org.springframework.stereotype.Service;


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
        velocityEngine.setProperty("classpath.resource.loader.class",
                ClasspathResourceLoader.class.getName());
        velocityEngine.init();

        templates.put(Template.RESET_ACCOUNT_PASSWORD, "templates/mail/resetAccountPassword.vm");
        templates.put(Template.ACCOUNT_USERNAME, "templates/mail/accountUsername.vm");
        templates.put(Template.ACCOUNT_PASSWORD, "templates/mail/accountPassword.vm");
        templates.put(Template.RESET_2FA, "templates/mail/reset2fa.vm");
        templates.put(Template.CHANGE_PASSWORD, "templates/mail/changePassword.vm");
     
    }

    @Override
    public String mergeTemplateToString(Template template, Map<String, Object> model) {
        org.apache.velocity.Template velocityTemplate =
                velocityEngine.getTemplate(templates.get(template));
        VelocityContext velocityContext = new VelocityContext(model);
        StringWriter writer = new StringWriter();
        velocityTemplate.merge(velocityContext, writer);
        return writer.toString();
    }
}
