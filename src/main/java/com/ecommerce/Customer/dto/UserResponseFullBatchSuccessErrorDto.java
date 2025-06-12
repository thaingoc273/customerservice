package com.ecommerce.Customer.dto;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserResponseFullBatchSuccessErrorDto {
    private HttpStatus status;
    private UserResponseBatchSuccessErrorDto body;
}
