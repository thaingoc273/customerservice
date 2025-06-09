package com.ecommerce.Customer.service.impl;

import com.ecommerce.Customer.dto.FullUserResponseDTO;
import com.ecommerce.Customer.dto.UserCallExternalRequestDTO;
import com.ecommerce.Customer.dto.UserCallExternalResponseDTO;
import com.ecommerce.Customer.dto.UserCallFullResponse;
import com.ecommerce.Customer.dto.UserDTO;
import com.ecommerce.Customer.mapper.UserMapper;
import com.ecommerce.Customer.repository.UserRepository;
import com.ecommerce.Customer.service.UserService;

import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
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
    private final Scheduler parallelScheduler = Schedulers.newParallel("user-save-scheduler", 5);
    private WebClient webClient;
    private String baseUrl;
    private String uri;
    private String currentUserLogin;

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
            log.info("Thread [{}] - Starting batch user creation for {} users", 
                Thread.currentThread().getName(), userCallExternalRequestDTOS.size());
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

            log.info("Thread [{}] - Split users into {} chunks for parallel processing", 
                Thread.currentThread().getName(), chunks.size());

            // Process each chunk in parallel
            List<CompletableFuture<List<UserCallExternalResponseDTO>>> futures = chunks.stream()
                .map(chunk -> CompletableFuture.supplyAsync(() -> {
                    try {
                        log.info("Thread [{}] - Starting to save chunk of {} users", 
                            Thread.currentThread().getName(), chunk.size());
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
                            .doOnNext(responses -> {
                                log.info("Thread [{}] - Successfully saved {} users in chunk", 
                                    Thread.currentThread().getName(), responses.size());
                                responses.forEach(response -> 
                                    log.info("Thread [{}] - Saved user: {}", 
                                        Thread.currentThread().getName(), response.getUsername()));
                            })
                            .block();
                    } catch (Exception e) {
                        log.error("Thread [{}] - Error processing chunk: {}", 
                            Thread.currentThread().getName(), e.getMessage());
                        throw new RuntimeException("Failed to process chunk: " + e.getMessage(), e);
                    }
                }, executorService))
                .collect(Collectors.toList());

            // Wait for all futures to complete and combine results
            List<UserCallExternalResponseDTO> allResponses = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList());

            log.info("Thread [{}] - Completed saving all {} users", 
                Thread.currentThread().getName(), allResponses.size());
            return allResponses;
        } catch (Exception e) {
            log.error("Thread [{}] - Error creating users: {}", 
                Thread.currentThread().getName(), e.getMessage());
            throw new RuntimeException("Failed to create users through external service: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public Flux<FullUserResponseDTO> createUserReactiveTest(String currentUser, List<UserCallExternalRequestDTO> userCallExternalRequestDTOS) {
        currentUserLogin = currentUser;
        baseUrl = "http://localhost:8082";
        uri = "/api/users/batch_async";

        // Create the web client
        webClient = createWebClient(baseUrl);

        // Create the chunks for the request
        List<List<UserCallExternalRequestDTO>> chunks = createChunks(userCallExternalRequestDTOS, 5);

        // Send the chunks to the server
      
        Flux<FullUserResponseDTO> flux = Flux.fromIterable(chunks)
                                                .flatMap(chunk -> Mono.<FullUserResponseDTO>defer(() -> sendChunkToServer(chunk))
                                                .publishOn(Schedulers.parallel()));

        // Return the flux
        return flux;                                         
    }

    private Mono<Boolean> checkPermission(String currentUser) {
        currentUserLogin = currentUser;
        baseUrl = "http://localhost:8082";
        uri = "/api/users/permission";

        WebClient webClient = createWebClient(baseUrl);

        return webClient.get()
            .uri(uri + "/" + currentUserLogin)
            .retrieve()
            .bodyToMono(String.class)
            .map(permission -> permission.contains("ADMIN"));
    }

    private WebClient createWebClient(String baseUrl) {
        // To configure a connection pool size
        ConnectionProvider provider = ConnectionProvider.builder("custom-connection-pool")
            .maxConnections(50)
            .pendingAcquireTimeout(Duration.ofSeconds(10))
            .pendingAcquireMaxCount(1000)
            .build();
        
        // To configure a connection timeout
        HttpClient httpClient = HttpClient.create(provider).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
        
        // Create the web client        
        webClient = WebClient.builder()
                                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                                .clientConnector(new ReactorClientHttpConnector(httpClient))
                                .baseUrl(baseUrl)                               
                                .build();
        return webClient;
    }

    private Mono<FullUserResponseDTO> sendChunkToServer(List<UserCallExternalRequestDTO> chunk) {
        return webClient.post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Current-User", currentUserLogin)
            .body(BodyInserters.fromValue(chunk))
            .exchangeToMono(response -> {
                log.info("Thread [{}] - Response status code: {}", 
                    Thread.currentThread().getName(), response.statusCode());
                return response.bodyToMono(UserCallFullResponse.class)
                    .map(body -> new FullUserResponseDTO(HttpStatus.valueOf(response.statusCode().value()), body));
            })
            .onErrorResume(WebClientResponseException.class, ex -> {
                log.error("WebClient error: {}", ex.getMessage());
                return Mono.error(new RuntimeException("Error processing request: " + ex.getMessage()));
            })
            ;
    }

    private List<List<UserCallExternalRequestDTO>> createChunks(List<UserCallExternalRequestDTO> requestDTOs, int numberOfChunks) {
        int chunkSize = (int) Math.ceil((double)requestDTOs.size() / numberOfChunks);
        List<List<UserCallExternalRequestDTO>> chunks = new ArrayList<>();
        for (int i = 0; i < requestDTOs.size(); i += chunkSize) {
            chunks.add(requestDTOs.subList(i, Math.min(i + chunkSize, requestDTOs.size())));
        }
        return chunks;
    }

    @Override
    @Transactional
    public Flux<UserCallFullResponse> createUserReactiveNew(String currentUser, List<UserCallExternalRequestDTO> userCallExternalRequestDTOS) {
        log.info("Thread [{}] - Starting reactive batch processing user creation for {} users", 
            Thread.currentThread().getName(), userCallExternalRequestDTOS.size());
        String url = "http://localhost:8082/api/users/batch_async";
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
        
        log.info("Thread [{}] - Split users into {} chunks for reactive processing", 
            Thread.currentThread().getName(), chunks.size());

        return Flux.fromIterable(chunks)
            .doOnNext(chunk -> log.info("Thread [{}] - Starting to save chunk of {} users", 
                Thread.currentThread().getName(), chunk.size()))
            .flatMap(chunk -> 
                Mono.<List<UserCallExternalRequestDTO>>fromCallable(() -> {
                    log.info("Thread [{}] - Processing chunk in parallel", 
                        Thread.currentThread().getName());
                    return chunk;
                })
                .subscribeOn(parallelScheduler)
                .flatMapMany(processedChunk -> webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Current-User", currentUser)
                    .body(BodyInserters.fromValue(processedChunk))
                    .exchangeToMono(response -> {
                        if (response.statusCode().is5xxServerError()) {
                            return response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RuntimeException("Server error: " + body)));
                        }
                        return response.bodyToMono(UserCallFullResponse.class);
                    })
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        log.error("WebClient error: {}", ex.getMessage());
                        return Mono.error(new RuntimeException("Error processing request: " + ex.getMessage()));
                    })
                    .flatMapMany(response -> Flux.just(response))
                    .timeout(Duration.ofSeconds(60))
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                        .filter(throwable -> throwable instanceof WebClientResponseException 
                            && ((WebClientResponseException) throwable).getStatusCode().is5xxServerError())
                        .doBeforeRetry(retrySignal -> 
                            log.warn("Retrying request after error: {}", retrySignal.failure().getMessage())))
                    .onErrorResume(e -> {
                        if (e instanceof org.springframework.web.context.request.async.AsyncRequestTimeoutException) {
                            log.error("Request timed out: {}", e.getMessage());
                            return Flux.error(new RuntimeException("Request timed out. Please try again later."));
                        }
                        if (e instanceof org.springframework.core.codec.DecodingException) {
                            log.error("Error decoding response: {}", e.getMessage());
                            return Flux.error(new RuntimeException("Error processing response: " + e.getMessage()));
                        }
                        log.error("Thread [{}] - Error processing chunk: {}", 
                            Thread.currentThread().getName(), e.getMessage());
                        return Flux.error(new RuntimeException("Failed to process chunk: " + e.getMessage(), e));
                    })), 5)
            .doOnNext(response -> log.info("Thread [{}] - Processed batch with success rate: {}", 
                Thread.currentThread().getName(), response.getSuccessRate()))
            .collectList()
            .doOnNext(responses -> log.info("Thread [{}] - All processed batches: {}", 
                Thread.currentThread().getName(), responses))
            .flatMapMany(Flux::fromIterable)
            .doOnComplete(() -> log.info("Thread [{}] - Completed saving all users in reactive stream", 
                Thread.currentThread().getName()))
            .doOnError(e -> log.error("Thread [{}] - Error in reactive stream: {}", 
                Thread.currentThread().getName(), e.getMessage()));
    }

    @Override
    @Transactional
    public Flux<UserCallExternalResponseDTO> createUserReactive(String currentUser, List<UserCallExternalRequestDTO> userCallExternalRequestDTOS) {
        log.info("Thread [{}] - Starting reactive batch user creation for {} users", 
            Thread.currentThread().getName(), userCallExternalRequestDTOS.size());
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
        
        System.out.println("chunks: " + chunks);

        log.info("Thread [{}] - Split users into {} chunks for reactive processing", 
            Thread.currentThread().getName(), chunks.size());

        // Convert chunks to Flux and process them in parallel using custom scheduler
        return Flux.fromIterable(chunks)
            .doOnNext(chunk -> log.info("Thread [{}] - Starting to save chunk of {} users", 
                Thread.currentThread().getName(), chunk.size()))
            .flatMap(chunk -> 
                Mono.<List<UserCallExternalRequestDTO>>fromCallable(() -> {
                    log.info("Thread [{}] - Processing chunk in parallel", 
                        Thread.currentThread().getName());
                    return chunk;
                })
                .subscribeOn(parallelScheduler)
                .flatMapMany(processedChunk -> webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Current-User", currentUser)
                    .body(BodyInserters.fromValue(processedChunk))
                    .retrieve()
                    .bodyToFlux(UserCallExternalResponseDTO.class)
                    .timeout(Duration.ofSeconds(30))
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                        .filter(throwable -> throwable instanceof WebClientResponseException 
                            && ((WebClientResponseException) throwable).getStatusCode().is5xxServerError()))
                    .onErrorResume(e -> {
                        log.error("Thread [{}] - Error processing chunk: {}", 
                            Thread.currentThread().getName(), e.getMessage());
                        return Flux.error(new RuntimeException("Failed to process chunk: " + e.getMessage(), e));
                    })), 5)
            .doOnNext(response -> log.info("Thread [{}] - Processed user: {}", 
                Thread.currentThread().getName(), response.getUsername()))
            .collectList()
            .doOnNext(responses -> log.info("Thread [{}] - All processed users: {}", 
                Thread.currentThread().getName(), responses))
            .flatMapMany(Flux::fromIterable)
            .doOnComplete(() -> log.info("Thread [{}] - Completed saving all users in reactive stream", 
                Thread.currentThread().getName()))
            .doOnError(e -> log.error("Thread [{}] - Error in reactive stream: {}", 
                Thread.currentThread().getName(), e.getMessage()));
    }

    @Override
    @Transactional
    public Mono<List<UserCallExternalResponseDTO>> createUserReactiveAsList(String currentUser, List<UserCallExternalRequestDTO> userCallExternalRequestDTOS) {
        return createUserReactive(currentUser, userCallExternalRequestDTOS)
            .collectList()
            .doOnSuccess(list -> log.info("Thread [{}] - Successfully collected {} responses", 
                Thread.currentThread().getName(), list.size()))
            .doOnError(e -> log.error("Thread [{}] - Error collecting responses: {}", 
                Thread.currentThread().getName(), e.getMessage()));
    }

    @Override
    @Transactional
    public List<UserCallExternalResponseDTO> createUserAsync(String currentUser, List<UserCallExternalRequestDTO> userCallExternalRequestDTOS) {
        try {
            log.info("Thread [{}] - Starting async batch user creation for {} users", 
                Thread.currentThread().getName(), userCallExternalRequestDTOS.size());

            // Split the list into chunks of equal size
            int chunkSize = (int) Math.ceil((double) userCallExternalRequestDTOS.size() / 5);
            List<List<UserCallExternalRequestDTO>> chunks = new ArrayList<>();
            
            for (int i = 0; i < userCallExternalRequestDTOS.size(); i += chunkSize) {
                chunks.add(userCallExternalRequestDTOS.subList(i, 
                    Math.min(i + chunkSize, userCallExternalRequestDTOS.size())));
            }

            log.info("Thread [{}] - Split users into {} chunks for async processing", 
                Thread.currentThread().getName(), chunks.size());

            // Process chunks in parallel using @Async
            List<CompletableFuture<List<UserCallExternalResponseDTO>>> futures = chunks.stream()
                .map(chunk -> processChunkAsync(currentUser, chunk))
                .collect(Collectors.toList());

            // Wait for all futures to complete and combine results
            List<UserCallExternalResponseDTO> allResponses = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList());

            log.info("Thread [{}] - Completed async saving of all {} users", 
                Thread.currentThread().getName(), allResponses.size());
            return allResponses;

        } catch (Exception e) {
            log.error("Thread [{}] - Error in async user creation: {}", 
                Thread.currentThread().getName(), e.getMessage());
            throw new RuntimeException("Failed to create users through external service: " + e.getMessage(), e);
        }
    }

    @Async
    protected CompletableFuture<List<UserCallExternalResponseDTO>> processChunkAsync(
            String currentUser, List<UserCallExternalRequestDTO> chunk) {
        try {
            log.info("Thread [{}] - Starting async processing of chunk with {} users", 
                Thread.currentThread().getName(), chunk.size());

            String url = "http://localhost:8082/api/users/batch";
            WebClient webClient = webClientBuilder
                    .build()
                    .mutate()
                    .build();

            List<UserCallExternalResponseDTO> responses = webClient.post()
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
                .doOnNext(response -> log.info("Thread [{}] - Processed user: {}", 
                    Thread.currentThread().getName(), response.getUsername()))
                .collectList()
                .block();

            log.info("Thread [{}] - Completed processing chunk with {} users", 
                Thread.currentThread().getName(), responses.size());
            return CompletableFuture.completedFuture(responses);

        } catch (Exception e) {
            log.error("Thread [{}] - Error processing chunk: {}", 
                Thread.currentThread().getName(), e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
} 