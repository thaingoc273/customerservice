package com.ecommerce.Customer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FullUserResponseDTO {
  private HttpStatus status;
  private UserCallFullResponse body;
}
