package com.ecommerce.Customer.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.ecommerce.Customer.dto.UserCallExternalRequestDTO;
import com.ecommerce.Customer.dto.UserCallExternalResponseDTO;
import com.ecommerce.Customer.dto.UserDTO;
import com.ecommerce.Customer.dto.UserResponseFullBatchSuccessErrorDto;
import com.ecommerce.Customer.service.UserService;
import com.ecommerce.Customer.util.UserDataCallExternalApiGenerator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class UserControllerFullIntegrationTest {

  @Autowired private WebTestClient webTestClient;

  @Autowired private UserService userService;

  @Autowired private UserDataCallExternalApiGenerator userDataGenerator;

  private static final String BASE_URL = "/api/v1/users";
  private static final String CURRENT_USER = "admin";

  @BeforeEach
  void setUp() {
    // Any setup code if needed
  }

  @Test
  void getAllUsers_ShouldReturnListOfUsers() {
    List<UserDTO> users =
        webTestClient
            .get()
            .uri(BASE_URL)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(UserDTO.class)
            .returnResult()
            .getResponseBody();

    assertNotNull(users);
    assertTrue(users.size() > 0);
  }

  @Test
  void generateFakeUsers_ShouldReturnGeneratedUsers() {
    int count = 5;
    webTestClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder.path(BASE_URL + "/generate").queryParam("count", count).build())
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBodyList(UserCallExternalRequestDTO.class)
        .hasSize(count)
        .consumeWith(
            result -> {
              List<UserCallExternalRequestDTO> response = result.getResponseBody();
              assertNotNull(response);
              response.forEach(
                  userResponse -> {
                    assertNotNull(userResponse.getUsername());
                    assertNotNull(userResponse.getPassword());
                    assertNotNull(userResponse.getEmail());
                  });
            });
  }

  @Test
  void createUser_ShouldCreateUsersSuccessfully() {
    List<UserCallExternalRequestDTO> users = userDataGenerator.generateUsers(3);

    webTestClient
        .post()
        .uri(BASE_URL + "/batch")
        .header("X-Current-User", CURRENT_USER)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(users)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBodyList(UserCallExternalResponseDTO.class)
        .hasSize(users.size());
  }

  @Test
  void createUserReactive_ShouldCreateUsersSuccessfully() {
    List<UserCallExternalRequestDTO> users = userDataGenerator.generateUsers(3);

    webTestClient
        .post()
        .uri(BASE_URL + "/batch/reactive")
        .header("X-Current-User", CURRENT_USER)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(users)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_NDJSON)
        .expectBodyList(UserCallExternalResponseDTO.class)
        .hasSize(users.size());
  }

  @Test
  void createUserReactiveAsList_ShouldCreateUsersSuccessfully() {
    List<UserCallExternalRequestDTO> users = userDataGenerator.generateUsers(3);

    webTestClient
        .post()
        .uri(BASE_URL + "/batch/reactive/list")
        .header("X-Current-User", CURRENT_USER)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(users)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBodyList(UserCallExternalResponseDTO.class)
        .hasSize(users.size());
  }

  @Test
  void createUserReactiveTest_ShouldCreateUsersSuccessfully() {
    List<UserCallExternalRequestDTO> users = userDataGenerator.generateUsers(3);

    webTestClient
        .post()
        .uri(BASE_URL + "/batch/reactive/test")
        .header("X-Current-User", CURRENT_USER)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(users)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBodyList(UserDTO.class)
        .hasSize(1);
  }

  @Test
  void createUserReactiveTestSuccessError_ShouldHandleSuccessAndError() {
    List<UserCallExternalRequestDTO> users = userDataGenerator.generateUsers(3);

    webTestClient
        .post()
        .uri(BASE_URL + "/batch/reactive/test/success_error")
        .header("X-Current-User", CURRENT_USER)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(users)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBodyList(UserResponseFullBatchSuccessErrorDto.class)
        .hasSize(1);
  }
}
