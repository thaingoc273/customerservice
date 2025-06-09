package com.ecommerce.Customer.controller;

import com.ecommerce.Customer.dto.UserCallExternalResponseDTO;
import com.ecommerce.Customer.dto.FullUserResponseDTO;
import com.ecommerce.Customer.dto.UserCallExternalRequestDTO;
import com.ecommerce.Customer.dto.UserDTO;
import com.ecommerce.Customer.dto.UserCallFullResponse;
import com.ecommerce.Customer.service.UserService;
import com.ecommerce.Customer.util.UserDataCallExternalApiGenerator;
import com.ecommerce.Customer.util.UserDataGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "APIs for managing users")
public class UserController {

    private final UserService userService;
    // private final UserDataGenerator userDataGenerator;
    private final UserDataCallExternalApiGenerator userDataCallExternalApiGenerator;

    @Operation(
        summary = "Get all users",
        description = "Retrieves a list of all users with their roles"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved all users",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UserDTO.class)
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content
        )
    })
    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @Operation(
        summary = "Generate fake users",
        description = "Generates a specified number of fake users for testing"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Successfully generated fake users",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UserDTO.class)
            )
        )
    })
    @GetMapping("/generate")
    public ResponseEntity<List<UserCallExternalRequestDTO>> generateFakeUsers(
            @Parameter(description = "Number of users to generate (default: 10)")
            @RequestParam(defaultValue = "10") int count) {
        return ResponseEntity.ok(userDataCallExternalApiGenerator.generateUsers(count));
    }

    @PostMapping("/batch")
    public ResponseEntity<List<UserCallExternalResponseDTO>> createUser(
            @RequestHeader("X-Current-User") String currentUser,
            @RequestBody(required = false) List<UserCallExternalRequestDTO> userCallExternalRequestDTO) {
        List<UserCallExternalRequestDTO> requests = userCallExternalRequestDTO != null ? 
            userCallExternalRequestDTO : 
            userDataCallExternalApiGenerator.generateUsers(20);
        return ResponseEntity.ok(userService.createUser(currentUser, requests));
    }

    @Operation(
        summary = "Create users reactively",
        description = "Creates users using a reactive approach with parallel processing"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Successfully created users",
            content = @Content(
                mediaType = MediaType.APPLICATION_NDJSON_VALUE,
                schema = @Schema(implementation = UserCallExternalResponseDTO.class)
            )
        )
    })
    @PostMapping(value = "/batch/reactive", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<UserCallExternalResponseDTO> createUserReactive(
            @RequestHeader("X-Current-User") String currentUser,
            @RequestBody(required = false) List<UserCallExternalRequestDTO> userCallExternalRequestDTO) {
        List<UserCallExternalRequestDTO> requests = userCallExternalRequestDTO != null ? 
            userCallExternalRequestDTO : 
            userDataCallExternalApiGenerator.generateUsers(20);
        return userService.createUserReactive(currentUser, requests);
    }

    @Operation(
        summary = "Create users reactively as list",
        description = "Creates users using a reactive approach and returns them as a list"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Successfully created users",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = UserCallExternalResponseDTO.class)
            )
        )
    })
    @PostMapping(value = "/batch/reactive/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<List<UserCallExternalResponseDTO>>> createUserReactiveAsList(
            @RequestHeader("X-Current-User") String currentUser,
            @RequestBody(required = false) List<UserCallExternalRequestDTO> userCallExternalRequestDTO) {
        List<UserCallExternalRequestDTO> requests = userCallExternalRequestDTO != null ? 
            userCallExternalRequestDTO : 
            userDataCallExternalApiGenerator.generateUsers(20);
        return userService.createUserReactiveAsList(currentUser, requests)
            .map(ResponseEntity::ok)
            .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError().build()));
    }

    @PostMapping("/batch/reactive/new")
    public Flux<UserCallFullResponse> createUserReactiveNew(
        @RequestHeader("X-Current-User") String currentUser,
        @RequestBody(required = false) List<UserCallExternalRequestDTO> userCallExternalRequestDTO) {
        List<UserCallExternalRequestDTO> requests = userCallExternalRequestDTO != null ? 
            userCallExternalRequestDTO : 
            userDataCallExternalApiGenerator.generateUsers(20);
        return userService.createUserReactiveNew(currentUser, requests);
    }

    @PostMapping("/batch/reactive/test")
    public Flux<FullUserResponseDTO> createUserReactiveTest(
            @RequestHeader("X-Current-User") String currentUser,
            @RequestBody(required = false) List<UserCallExternalRequestDTO> userCallExternalRequestDTO) {
        List<UserCallExternalRequestDTO> requests = userCallExternalRequestDTO != null ? 
            userCallExternalRequestDTO : 
            userDataCallExternalApiGenerator.generateUsers(20);
        return userService.createUserReactiveTest(currentUser, requests);
    }
} 