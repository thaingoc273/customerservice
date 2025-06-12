package com.ecommerce.Customer.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserResponseBatchSuccessErrorDto {    
    private int successCount;
    private int failureCount;
    private float successRate;
    private List<UserBatchSuccessfulDto> successfulUsers;
    private List<UserBatchErrorDto> errorUsers;
}