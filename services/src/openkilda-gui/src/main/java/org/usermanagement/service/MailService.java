package org.usermanagement.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.openkilda.utility.StringUtil;
import org.usermanagement.util.CollectionUtils;



/**
 * Service Layer of sending emails.
 */
@Service
public class MailService {

    /** The Constant _log. */
    private static final Logger _log = LoggerFactory.getLogger(MailService.class);

    @Autowired
    private TemplateService templateService;

    @Autowired
    private JavaMailSender javaMailSender;

    /**
     * Sending message.
     *
     * @param receivers the list of receivers.
     * @param subject mail subject.
     * @param template template.
     * @param context Map with context values for velocity template.
     */
    public void send(final List<String> receivers, final String subject, final TemplateService.Template template,
            final Map<String, Object> context) {
        // SimpleMailMessage msg = new SimpleMailMessage();
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper msg = new MimeMessageHelper(mimeMessage);
        if (!CollectionUtils.isNullOrEmpty(receivers)) {

            try {
                msg.setSubject(subject);
                msg.setTo(receivers.toArray(new String[receivers.size()]));
                msg.setText(templateService.mergeTemplateToString(template, context), true);
            } catch (MessagingException e) {
                e.printStackTrace();
            }

            javaMailSender.send(mimeMessage);
            //
        }
    }

    /**
     * Sending message.
     *
     * @param receivers the list of receivers.
     * @param subject mail subject.
     * @param template template.
     * @param context Map with context values for velocity template.
     */
    public void send(final String receiver, final String subject, final TemplateService.Template template, final Map<String, Object> context) {
        // SimpleMailMessage msg = new SimpleMailMessage();
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper msg = new MimeMessageHelper(mimeMessage);
        if (!StringUtil.isNullOrEmpty(receiver)) {

            try {
                msg.setSubject(subject);
                msg.setTo(receiver);
                msg.setText(templateService.mergeTemplateToString(template, context), true);
            } catch (MessagingException e) {
                e.printStackTrace();
            }

            javaMailSender.send(mimeMessage);
            //
        }
    }


    /**
     * Send feedback.
     *
     * @param subject the subject
     * @param template the template
     * @param context the context
     */
    public void sendFeedback(final String subject, final TemplateService.Template template, final Map<String, Object> context,
            final String receiver) {

        _log.info("[sendFeedback]-start");

        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper msg = new MimeMessageHelper(mimeMessage);
        try {
            msg.setSubject(subject);
            msg.setTo(receiver);
            msg.setText(templateService.mergeTemplateToString(template, context), true);
            _log.info("[sendFeedback]-sending mail with parms-to:" + receiver);

        } catch (MessagingException e) {
            e.printStackTrace();
        }
        javaMailSender.send(mimeMessage);
        _log.info("[sendFeedback]-end");
    }
}
