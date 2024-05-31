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

package org.usermanagement.service;

import org.openkilda.utility.StringUtil;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service Layer of sending emails.
 */
@Slf4j
@Service
public class MailService {

    @Autowired
    private TemplateService templateService;

    @Autowired
    private JavaMailSender javaMailSender;

    @Value("${mail.from}")
    private String from;

    /**
     * Sending message.
     *
     * @param receivers the list of receivers.
     * @param subject   mail subject.
     * @param template  template.
     * @param context   Map with context values for velocity template.
     */
    public void send(final List<String> receivers, final String subject, final TemplateService.Template template,
                     final Map<String, Object> context) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper msg = new MimeMessageHelper(mimeMessage);
        if (!CollectionUtils.isEmpty(receivers)) {

            try {
                msg.setFrom(from);
                msg.setSubject(subject);
                msg.setTo(receivers.toArray(new String[receivers.size()]));
                msg.setText(templateService.mergeTemplateToString(template, context), true);

                javaMailSender.send(mimeMessage);
                log.info("Mail sent successfully. Subject: " + subject);
            } catch (MessagingException e) {
                log.error("Failed to send mail ", e);
            }
        }
    }

    /**
     * Sending message.
     *
     * @param receiver the receiver.
     * @param subject  mail subject.
     * @param template template.
     * @param context  Map with context values for velocity template.
     */
    public void send(final String receiver, final String subject, final TemplateService.Template template,
                     final Map<String, Object> context) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper msg = new MimeMessageHelper(mimeMessage);
        if (!StringUtil.isNullOrEmpty(receiver)) {

            try {
                msg.setFrom(from);
                msg.setSubject(subject);
                msg.setTo(receiver);
                msg.setText(templateService.mergeTemplateToString(template, context), true);

                javaMailSender.send(mimeMessage);
                log.info("Mail sent successfully. Subject: " + subject);
            } catch (MessagingException e) {
                log.error("Failed to send mail", e);
            }
        }
    }
}
