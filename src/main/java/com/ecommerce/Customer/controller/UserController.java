package com.ecommerce.Customer.controller;

import com.ecommerce.Customer.dto.UserCallExternalResponseDTO;
import com.ecommerce.Customer.dto.FullUserResponseDTO;
import com.ecommerce.Customer.dto.UserCallExternalRequestDTO;
import com.ecommerce.Customer.dto.UserDTO;
import com.ecommerce.Customer.dto.UserResponseBatchSuccessErrorDto;
import com.ecommerce.Customer.dto.UserResponseFullBatchSuccessErrorDto;
import com.ecommerce.Customer.dto.UserCallFullResponse;
import com.ecommerce.Customer.service.UserService;
import com.ecommerce.Customer.util.UserDataCallExternalApiGenerator;
import com.ecommerce.Customer.util.UserDataGenerator;

import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.WriteTimeoutException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
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

    // @PostMapping("/batch/reactive/new")
    // public Flux<UserCallFullResponse> createUserReactiveNew(
    //     @RequestHeader("X-Current-User") String currentUser,
    //     @RequestBody(required = false) List<UserCallExternalRequestDTO> userCallExternalRequestDTO) {
    //     List<UserCallExternalRequestDTO> requests = userCallExternalRequestDTO != null ? 
    //         userCallExternalRequestDTO : 
    //         userDataCallExternalApiGenerator.generateUsers(20);
    //     return userService.createUserReactiveNew(currentUser, requests);
    // }

    @PostMapping("/batch/reactive/test")
    public Flux<FullUserResponseDTO> createUserReactiveTest(
            @RequestHeader("X-Current-User") String currentUser,
            @RequestBody(required = false) List<UserCallExternalRequestDTO> userCallExternalRequestDTO) {
        List<UserCallExternalRequestDTO> requests = userCallExternalRequestDTO != null ? 
            userCallExternalRequestDTO : 
            userDataCallExternalApiGenerator.generateUsers(20);
        return userService.createUserReactiveTest(currentUser, requests);
    }

    @PostMapping("/batch/reactive/test/success_error")
    public Mono<ResponseEntity<Flux<UserResponseFullBatchSuccessErrorDto>>> createUserReactiveTestSuccessError(
            @RequestHeader("X-Current-User") String currentUser,
            @RequestBody(required = false) List<UserCallExternalRequestDTO> userCallExternalRequestDTO) {
        List<UserCallExternalRequestDTO> requests = userCallExternalRequestDTO != null ? 
            userCallExternalRequestDTO : 
            userDataCallExternalApiGenerator.generateUsers(20);
        
    return userService.createUserReactiveTestSuccessError(currentUser, requests)
        .collectList()
        .flatMap(results -> {
            int successCount = (int) results.stream()
                .filter(r -> r.getHttpStatus().equals(HttpStatus.OK))
                .count();
            
            HttpStatus responseStatus;
            if (successCount < 3) {
                responseStatus = HttpStatus.BAD_REQUEST;
            } else if (successCount == 3) {
                responseStatus = HttpStatus.PARTIAL_CONTENT;
            } else if (successCount >= 4) {
                responseStatus = HttpStatus.OK;
            } else {
                responseStatus = HttpStatus.FORBIDDEN;
            }
            
            // Return the original results with the appropriate status
            return Mono.just(ResponseEntity.status(responseStatus)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Flux.fromIterable(results)));
        })
        .onErrorResume(e -> {
            HttpStatus errorStatus = HttpStatus.INTERNAL_SERVER_ERROR;
            if (e instanceof WebClientResponseException) {
                errorStatus = HttpStatus.valueOf(((WebClientResponseException) e).getStatusCode().value());
            } else if (e instanceof ReadTimeoutException || e instanceof WriteTimeoutException) {
                errorStatus = HttpStatus.GATEWAY_TIMEOUT;
            }
            
            UserResponseFullBatchSuccessErrorDto errorResponse = new UserResponseFullBatchSuccessErrorDto(
                errorStatus,
                new UserResponseBatchSuccessErrorDto(0, 0, 0.0f, new ArrayList<>(), new ArrayList<>())
            );
            
            return Mono.just(ResponseEntity.status(errorStatus)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Flux.just(errorResponse)));
        });
    }
}
