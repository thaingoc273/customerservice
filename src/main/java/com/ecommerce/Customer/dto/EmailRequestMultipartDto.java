package com.ecommerce.Customer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmailRequestMultipartDto {
  private String to;
  private String subject;
  private String text;
  private String attachmentName;
}
