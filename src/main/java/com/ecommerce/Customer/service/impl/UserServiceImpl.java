package com.ecommerce.Customer.service.impl;

import com.ecommerce.Customer.dto.UserCallExternalRequestDTO;
import com.ecommerce.Customer.dto.UserCallExternalResponseDTO;
import com.ecommerce.Customer.dto.UserDTO;
import com.ecommerce.Customer.mapper.UserMapper;
import com.ecommerce.Customer.repository.UserRepository;
import com.ecommerce.Customer.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final WebClient.Builder webClientBuilder;
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    @Override
    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers() {
        return userRepository.findAllUsersWithRoles().stream()
                .map(userMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<UserCallExternalResponseDTO> createUser(String currentUser, List<UserCallExternalRequestDTO> userCallExternalRequestDTOS) {
        try {
            String url = "http://localhost:8082/api/users/batch";
            WebClient webClient = webClientBuilder
                    .build()
                    .mutate()
                    .build();

            // Split the list into chunks of equal size
            int chunkSize = (int) Math.ceil((double) userCallExternalRequestDTOS.size() / 5);
            List<List<UserCallExternalRequestDTO>> chunks = new ArrayList<>();
            
            for (int i = 0; i < userCallExternalRequestDTOS.size(); i += chunkSize) {
                chunks.add(userCallExternalRequestDTOS.subList(i, 
                    Math.min(i + chunkSize, userCallExternalRequestDTOS.size())));
            }

            // Process each chunk in parallel
            List<CompletableFuture<List<UserCallExternalResponseDTO>>> futures = chunks.stream()
                .map(chunk -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return webClient.post()
                            .uri(url)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Current-User", currentUser)
                            .body(BodyInserters.fromValue(chunk))
                            .retrieve()
                            .bodyToFlux(UserCallExternalResponseDTO.class)
                            .timeout(Duration.ofSeconds(30))
                            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                                    .filter(throwable -> throwable instanceof WebClientResponseException 
                                            && ((WebClientResponseException) throwable).getStatusCode().is5xxServerError()))
                            .collectList()
                            .block();
                    } catch (Exception e) {
                        log.error("Error processing chunk: {}", e.getMessage());
                        throw new RuntimeException("Failed to process chunk: " + e.getMessage(), e);
                    }
                }, executorService))
                .collect(Collectors.toList());

            // Wait for all futures to complete and combine results
            List<UserCallExternalResponseDTO> allResponses = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList());

            return allResponses;
        } catch (Exception e) {
            log.error("Error creating users: {}", e.getMessage());
            throw new RuntimeException("Failed to create users through external service: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public Flux<UserCallExternalResponseDTO> createUserReactive(String currentUser, List<UserCallExternalRequestDTO> userCallExternalRequestDTOS) {
        String url = "http://localhost:8082/api/users/batch";
        WebClient webClient = webClientBuilder
                .build()
                .mutate()
                .build();

        // Split the list into chunks of equal size
        int chunkSize = (int) Math.ceil((double) userCallExternalRequestDTOS.size() / 5);
        List<List<UserCallExternalRequestDTO>> chunks = new ArrayList<>();
        
        for (int i = 0; i < userCallExternalRequestDTOS.size(); i += chunkSize) {
            chunks.add(userCallExternalRequestDTOS.subList(i, 
                Math.min(i + chunkSize, userCallExternalRequestDTOS.size())));
        }

        // Convert chunks to Flux and process them in parallel
        return Flux.fromIterable(chunks)
            .flatMap(chunk -> webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Current-User", currentUser)
                .body(BodyInserters.fromValue(chunk))
                .retrieve()
                .bodyToFlux(UserCallExternalResponseDTO.class)
                .timeout(Duration.ofSeconds(30))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                    .filter(throwable -> throwable instanceof WebClientResponseException 
                        && ((WebClientResponseException) throwable).getStatusCode().is5xxServerError()))
                .onErrorResume(e -> {
                    log.error("Error processing chunk: {}", e.getMessage());
                    return Flux.error(new RuntimeException("Failed to process chunk: " + e.getMessage(), e));
                }), 5) // Process up to 5 chunks concurrently
            .doOnNext(response -> log.info("Processed user: {}", response.getUsername()))
            .doOnError(e -> log.error("Error in reactive stream: {}", e.getMessage()));
    }

    @Override
    @Transactional
    public Mono<List<UserCallExternalResponseDTO>> createUserReactiveAsList(String currentUser, List<UserCallExternalRequestDTO> userCallExternalRequestDTOS) {
        return createUserReactive(currentUser, userCallExternalRequestDTOS)
            .collectList()
            .doOnSuccess(list -> log.info("Successfully collected {} responses", list.size()))
            .doOnError(e -> log.error("Error collecting responses: {}", e.getMessage()));
    }
} 