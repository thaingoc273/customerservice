package com.ecommerce.Customer.dto;

import lombok.Data;

@Data
public class UserCallBatchResponse {
  private String username;
  private String status;
  private String message;
}
