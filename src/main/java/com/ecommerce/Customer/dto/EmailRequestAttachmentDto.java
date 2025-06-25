package com.ecommerce.Customer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmailRequestAttachmentDto {
  private String to;
  private String subject;
  private String text;
  private String attachmentName;
  private String attachmentPath;
}
