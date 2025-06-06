package com.ecommerce.Customer.service;

import com.ecommerce.Customer.dto.UserCallExternalRequestDTO;
import com.ecommerce.Customer.dto.UserCallExternalResponseDTO;
import com.ecommerce.Customer.dto.UserDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface UserService {
    List<UserDTO> getAllUsers();
    List<UserCallExternalResponseDTO> createUser(String currentUser, List<UserCallExternalRequestDTO> userCallExternalRequestDTO);
    Flux<UserCallExternalResponseDTO> createUserReactive(String currentUser, List<UserCallExternalRequestDTO> userCallExternalRequestDTO);
    Mono<List<UserCallExternalResponseDTO>> createUserReactiveAsList(String currentUser, List<UserCallExternalRequestDTO> userCallExternalRequestDTO);
} 