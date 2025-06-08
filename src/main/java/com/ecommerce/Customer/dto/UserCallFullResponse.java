package com.ecommerce.Customer.dto;

import java.util.List;

import lombok.Data;

@Data
public class UserCallFullResponse {
    // private int batchNumber;
    // private String statusCode;
    // private String statusMessage;

    private int successCount;
    private int failureCount;
    private float successRate;
    private List<UserCallExternalResponseDTO> results;
}