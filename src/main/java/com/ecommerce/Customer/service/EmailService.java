package com.ecommerce.Customer.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EmailService {
  @Autowired private JavaMailSender mailSender;

  public void sendEmail(String to, String subject, String text) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(to);
    message.setSubject(subject);
    message.setText(text);
    mailSender.send(message);
  }

  public void sendEmailWithAttachment(
      String to, String subject, String text, String attachmentName, String attachmentPath) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true);
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(text);
      helper.addAttachment(attachmentName, new File(attachmentPath));
      mailSender.send(message);
    } catch (MessagingException e) {
      throw new RuntimeException("Failed to send email with attachment", e);
    }
  }

  public void sendEmailWithInputStreamAttachment(
      String to, String subject, String text, String attachmentName, InputStream inputStream) {
    try {
      // Read the input stream into a byte array to create a ByteArrayResource
      byte[] attachmentData = inputStream.readAllBytes();
      inputStream.close();

      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true);
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(text);
      helper.addAttachment(attachmentName, new ByteArrayResource(attachmentData));
      mailSender.send(message);
    } catch (MessagingException | IOException e) {
      throw new RuntimeException("Failed to send email with input stream attachment", e);
    }
  }

  public void sendEmailWithMultipartFileAttachment(
      String to, String subject, String text, String attachmentName, MultipartFile file) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true);
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(text);
      helper.addAttachment(attachmentName, file);
      mailSender.send(message);
    } catch (MessagingException e) {
      throw new RuntimeException("Failed to send email with multipart file attachment", e);
    }
  }
}
