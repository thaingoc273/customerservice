package com.ecommerce.Customer.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
public class EmailServiceTest {

  @Mock private JavaMailSender javaMailSender;

  @InjectMocks private EmailService emailService;

  private static final Logger log = LoggerFactory.getLogger(EmailServiceTest.class);

  @Test
  void sendEmail_ShouldReturnSuccessful() throws Exception {
    String to = "test@example.com";
    String subject = "Test subject";
    String body = "Test body";

    emailService.sendEmail(to, subject, body);

    ArgumentCaptor<SimpleMailMessage> messageCaptor =
        ArgumentCaptor.forClass(SimpleMailMessage.class);

    verify(javaMailSender).send(messageCaptor.capture());

    SimpleMailMessage sentMessage = messageCaptor.getValue();
    assertEquals(subject, sentMessage.getSubject());
    assertEquals(body, sentMessage.getText());
    assertArrayEquals(new String[] {to}, sentMessage.getTo());
    verify(javaMailSender).send(messageCaptor.capture());
  }

  @Test
  void sendEmailWithAttachment_ShouldReturnSuccessful() throws Exception {
    String to = "test@example.com";
    String subject = "Test subject";
    String body = "Test body";
    String attachmentName = "test.txt";
    String attachmentPath = "test.txt";

    MimeMessage mimeMessage = new MimeMessage((Session) null);
    when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
    emailService.sendEmailWithAttachment(to, subject, body, attachmentName, attachmentPath);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(javaMailSender).send(messageCaptor.capture());

    MimeMessage sentMessage = messageCaptor.getValue();
    assertTrue(sentMessage.getSubject().contains(subject));
    assertEquals(to, sentMessage.getRecipients(MimeMessage.RecipientType.TO)[0].toString());
  }

  @Test
  void sendEmailWithInputStreamAttachment_ShouldReturnSuccessful() throws Exception {
    String to = "test@example.com";
    String subject = "Test subject";
    String body = "Test body";
    String attachmentName = "test.txt";

    // Create a real ByteArrayInputStream with test content
    byte[] testContent = "test content".getBytes();

    // Let the EmailService create its own MimeMessage
    when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

    // Create a new stream each time the method is called
    emailService.sendEmailWithInputStreamAttachment(
        to, subject, body, attachmentName, new ByteArrayInputStream(testContent));

    // Verify the email was sent
    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(javaMailSender).send(messageCaptor.capture());

    // Verify the message details
    MimeMessage sentMessage = messageCaptor.getValue();
    assertTrue(sentMessage.getSubject().contains(subject));
    assertEquals(to, sentMessage.getRecipients(MimeMessage.RecipientType.TO)[0].toString());
  }

  @Test
  void sendEmailWithMultipartFileAttachment_ShouldReturnSuccessful() throws Exception {
    String to = "test@example.com";
    String subject = "Test subject";
    String body = "Test body";
    String attachmentName = "test.txt";

    // Create a mock MultipartFile
    MultipartFile mockFile = mock(MultipartFile.class);

    // Let the EmailService create its own MimeMessage
    when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

    emailService.sendEmailWithMultipartFileAttachment(to, subject, body, attachmentName, mockFile);

    // Verify the email was sent
    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(javaMailSender).send(messageCaptor.capture());

    // Verify the message details
    MimeMessage sentMessage = messageCaptor.getValue();
    assertTrue(sentMessage.getSubject().contains(subject));
    assertEquals(to, sentMessage.getRecipients(MimeMessage.RecipientType.TO)[0].toString());
  }
}
