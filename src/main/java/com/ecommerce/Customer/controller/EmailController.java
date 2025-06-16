package com.ecommerce.Customer.controller;

import com.ecommerce.Customer.service.EmailService;
import com.ecommerce.Customer.dto.EmailRequestAttachmentDto;
import com.ecommerce.Customer.dto.EmailRequestDto;
import com.ecommerce.Customer.dto.EmailRequestMultipartDto;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/email")
public class EmailController {
    @Autowired
    private EmailService emailService;
    
    // public EmailController(EmailServive emailService) {
    //     this.emailService = emailService;
    // }
    
    @PostMapping("/send")
    public ResponseEntity<String> sendEmail(@RequestBody EmailRequestDto request){
        emailService.sendEmail(request.getTo(),request.getSubject(),request.getText());
        return ResponseEntity.ok("Email sent successfully to "+request.getTo());
    }

    @PostMapping("/send/attachment")
    public ResponseEntity<String> sendEmailWithAttachment(@RequestBody EmailRequestAttachmentDto request) {
        emailService.sendEmailWithAttachment(request.getTo(), request.getSubject(), request.getText(), request.getAttachmentName(), request.getAttachmentPath());
        return ResponseEntity.ok("Email sent successfully to "+request.getTo());
    }   

    @PostMapping("/send/multipart")
    public ResponseEntity<String> sendEmailWithMultipartFileAttachment(
                                        @RequestParam String to,
                                        @RequestParam String subject,
                                        @RequestParam String text,
                                        @RequestParam String attachmentName,
                                        @RequestParam("file") MultipartFile file) {
        emailService.sendEmailWithMultipartFileAttachment(to, subject, text, attachmentName, file);
        return ResponseEntity.ok("Email sent successfully to "+to);
    }

    @PostMapping("/send/multipart/new")//, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> sendEmailWithMultipartFileAttachmentNew(
            @RequestPart("to") String to,
            @RequestPart("subject") String subject,
            @RequestPart("text") String text,
            @RequestPart("attachmentName") String attachmentName,
            @RequestPart("file") MultipartFile file) {
        emailService.sendEmailWithMultipartFileAttachment(to, subject, text, attachmentName, file);
        return ResponseEntity.ok("Email sent successfully to " + to);
    }
}
