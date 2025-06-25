package com.ecommerce.Customer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ecommerce.Customer.dto.UserCallExternalRequestDTO;
import com.ecommerce.Customer.dto.UserResponseBatchSuccessErrorDto;
import com.ecommerce.Customer.dto.UserResponseFullBatchSuccessErrorDto;
import com.ecommerce.Customer.service.UserService;
import com.ecommerce.Customer.util.UserDataCallExternalApiGenerator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

@WebFluxTest(UserController.class)
public class UserControllerWebFluxTest {

  @Autowired private WebTestClient webTestClient;

  @MockBean private UserService userService;

  @MockBean private UserDataCallExternalApiGenerator userDataCallExternalApiGenerator;

  private UserResponseFullBatchSuccessErrorDto successResponse;
  private UserResponseFullBatchSuccessErrorDto forbiddenResponse;

  @BeforeEach
  void setUp() {
    // Initialize success response
    successResponse =
        new UserResponseFullBatchSuccessErrorDto(
            HttpStatus.OK,
            new UserResponseBatchSuccessErrorDto(
                1, 0, 100.0f, new ArrayList<>(), new ArrayList<>()));

    // Initialize forbidden response
    forbiddenResponse =
        new UserResponseFullBatchSuccessErrorDto(
            HttpStatus.FORBIDDEN,
            new UserResponseBatchSuccessErrorDto(0, 0, 0.0f, new ArrayList<>(), new ArrayList<>()));

    // Configure WebTestClient to disable CSRF
    webTestClient = webTestClient.mutateWith(SecurityMockServerConfigurers.csrf());
  }

  @Test
  @WithMockUser(username = "ngoc")
  void testCreateUserReactiveTestSuccessError_WithPermission() {
    // Prepare test data
    UserCallExternalRequestDTO requestDTO = new UserCallExternalRequestDTO();
    requestDTO.setUsername("testuser");
    requestDTO.setPassword("12345678abc");
    requestDTO.setEmail("testuser@gmail.com");

    // Mock the service response with permission
    when(userService.createUserReactiveTestSuccessError(eq("ngoc"), any()))
        .thenReturn(Flux.just(successResponse));

    // Perform the test
    webTestClient
        .post()
        .uri("/api/v1/users/batch/reactive/test/success_error")
        .header("X-Current-User", "ngoc")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(List.of(requestDTO))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBodyList(UserResponseFullBatchSuccessErrorDto.class)
        .hasSize(1)
        .contains(successResponse);
  }

  @Test
  @WithMockUser(username = "user_without_permission")
  void testCreateUserReactiveTestSuccessError_WithoutPermission() {
    // Prepare test data
    UserCallExternalRequestDTO requestDTO = new UserCallExternalRequestDTO();
    requestDTO.setUsername("testuser");
    requestDTO.setPassword("12345678abc");
    requestDTO.setEmail("testuser@gmail.com");

    // Mock the service response without permission
    when(userService.createUserReactiveTestSuccessError(eq("user_without_permission"), any()))
        .thenReturn(Flux.just(forbiddenResponse));

    // Perform the test
    webTestClient
        .post()
        .uri("/api/v1/users/batch/reactive/test/success_error")
        .header("X-Current-User", "user_without_permission")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(List.of(requestDTO))
        .exchange()
        .expectStatus()
        .isBadRequest() // The endpoint returns 200 OK even for forbidden responses
        .expectBodyList(UserResponseFullBatchSuccessErrorDto.class)
        .hasSize(1)
        .contains(forbiddenResponse);
  }

  @Test
  @WithMockUser(username = "ngoc")
  void testCreateUserReactiveTestSuccessError_WithGeneratedUsers() {
    // Prepare test data
    UserCallExternalRequestDTO generatedRequest = new UserCallExternalRequestDTO();
    generatedRequest.setUsername("generateduser");
    generatedRequest.setPassword("12345678abc");
    generatedRequest.setEmail("generateduser@gmail.com");

    // Mock the generator
    when(userDataCallExternalApiGenerator.generateUsers(anyInt()))
        .thenReturn(List.of(generatedRequest));

    // Mock the service response with permission
    when(userService.createUserReactiveTestSuccessError(eq("ngoc"), any()))
        .thenReturn(Flux.just(successResponse));

    // Perform the test without request body (should use generated users)
    webTestClient
        .post()
        .uri("/api/v1/users/batch/reactive/test/success_error")
        .header("X-Current-User", "ngoc")
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBodyList(UserResponseFullBatchSuccessErrorDto.class)
        .hasSize(1)
        .contains(successResponse);
  }

  @Test
  @WithMockUser(username = "ngoc")
  void testCreateUserReactiveTestSuccessError_ErrorHandling() {
    // Mock service to throw an exception
    when(userService.createUserReactiveTestSuccessError(eq("ngoc"), any()))
        .thenReturn(Flux.error(new RuntimeException("Test error")));

    // Perform the test
    webTestClient
        .post()
        .uri("/api/v1/users/batch/reactive/test/success_error")
        .header("X-Current-User", "ngoc")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(List.of(new UserCallExternalRequestDTO()))
        .exchange()
        .expectStatus()
        .is5xxServerError();
  }
}
