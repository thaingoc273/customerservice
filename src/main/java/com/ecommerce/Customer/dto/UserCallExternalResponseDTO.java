package com.ecommerce.Customer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCallExternalResponseDTO {
  // private String id;
  private String username;
  private String status;
  private String message;
  // private LocalDate birthday;
  // private String address;
  // private LocalDateTime createdAt;
  // private LocalDateTime updatedAt;
  // private Set<RoleDTO> roles;
}
