package com.ecommerce.Customer.service.impl;

import com.ecommerce.Customer.dto.FullUserResponseDTO;
import com.ecommerce.Customer.dto.UserCallExternalRequestDTO;
import com.ecommerce.Customer.dto.UserCallExternalResponseDTO;
import com.ecommerce.Customer.dto.UserCallFullResponse;
import com.ecommerce.Customer.dto.UserDTO;
import com.ecommerce.Customer.dto.UserResponseBatchSuccessErrorDto;
import com.ecommerce.Customer.dto.UserResponseFullBatchSuccessErrorDto;
import com.ecommerce.Customer.mapper.UserMapper;
import com.ecommerce.Customer.repository.UserRepository;
import com.ecommerce.Customer.service.UserService;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.PrematureCloseException;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.retry.Retry;

import java.net.ConnectException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
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
    private final String baseUrl = "http://localhost:8082";
    private final String uri_batch_success_error = "/api/users/batch_success_error";
    private final String uri_batch_async = "/api/users/batch_async";

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
        String baseUrl = "http://localhost:8082";
        String uri = "/api/users/batch_async";

        // Create the web client
        WebClient webClient = createWebClient(baseUrl);

        // Create the chunks for the request
        List<List<UserCallExternalRequestDTO>> chunks = createChunks(userCallExternalRequestDTOS, 5);

        return checkPermission(currentUser)
            .flatMapMany(permission -> {
                if (!permission) {
                    log.error("User does not have permission to create users");
                    return Flux.just(new FullUserResponseDTO(
                        HttpStatus.FORBIDDEN,
                        new UserCallFullResponse(0, 0, 0.0f, new ArrayList<>())
                    ));
                }

                return Flux.fromIterable(chunks)
                    .flatMap(chunk -> Mono.<FullUserResponseDTO>defer(() -> sendChunkToServer(chunk, webClient, currentUser))
                    .subscribeOn(parallelScheduler));
                    // .publishOn(Schedulers.parallel()));
            })
            .onErrorResume(WebClientResponseException.class, ex -> {
                log.error("WebClient error: {}", ex.getMessage());
                return Flux.just(new FullUserResponseDTO(
                    HttpStatus.valueOf(ex.getStatusCode().value()),
                    new UserCallFullResponse(0, 1, 0.0f, new ArrayList<>())
                ));
            });
 

    }

    @Override
    @Transactional
    public Flux<UserResponseFullBatchSuccessErrorDto> createUserReactiveTestSuccessError(String currentUser, List<UserCallExternalRequestDTO> userCallExternalRequestDTOS) {

        // Create the web client
        WebClient webClient = createWebClient(baseUrl);

        // Create the chunks for the request
        List<List<UserCallExternalRequestDTO>> chunks = createChunks(userCallExternalRequestDTOS, 5);

        return checkPermission(currentUser)
            .flatMapMany(permission -> {
                if (!permission) {
                    log.error("User does not have permission to create users");
                    return Flux.just(new UserResponseFullBatchSuccessErrorDto(
                        HttpStatus.FORBIDDEN,
                        new UserResponseBatchSuccessErrorDto(0, 0, 0.0f, new ArrayList<>(), new ArrayList<>())
                    ));
                }

                return Flux.fromIterable(chunks)
                    .flatMap(chunk -> Mono.<UserResponseFullBatchSuccessErrorDto>defer(() -> sendChunkToServerSuccessError(chunk, webClient, uri_batch_success_error, currentUser))
                    .subscribeOn(parallelScheduler));
                    // .publishOn(Schedulers.parallel()));
            })
            .onErrorResume(WebClientResponseException.class, ex -> {
                log.error("WebClient error: {}", ex.getMessage());
                return Flux.just(new UserResponseFullBatchSuccessErrorDto(
                    HttpStatus.valueOf(ex.getStatusCode().value()),
                    new UserResponseBatchSuccessErrorDto(0, 0, 0.0f, new ArrayList<>(), new ArrayList<>())
                ));
            });
    }

    private Mono<Boolean> checkPermission(String currentUser) {
        String baseUrl = "http://localhost:8082";
        String uri = "/api/users/permission";

        WebClient webClient = createWebClient(baseUrl);

        return webClient.get()
            .uri(uri + "/" + currentUser)
            .retrieve()
            .onStatus(status -> status.is5xxServerError(),
                response -> response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new RuntimeException("Server error: " + body))))
            .bodyToMono(String.class)
            .map(permission -> {
                log.info("Permission: {}", permission);
                log.info("Permission contain ADMIN: {}", permission.contains("ADMIN"));
                return permission.contains("ADMIN");
            })
            .onErrorResume(WebClientRequestException.class, ex -> {
                log.error("Permission check error: {}", ex.getMessage());
                return Mono.just(false);
            })            
            ;
    }

    private WebClient createWebClient(String baseUrl) {
        // To configure a connection pool size
        ConnectionProvider provider = ConnectionProvider.builder("custom-connection-pool")
            .maxConnections(50)
            .pendingAcquireTimeout(Duration.ofSeconds(10))
            .pendingAcquireMaxCount(1000)
            .build();
        
        // To configure a connection timeout
        HttpClient httpClient = HttpClient.create(provider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 100000)
            .responseTimeout(Duration.ofSeconds(120))
            .doOnConnected(conn -> 
                conn.addHandlerLast(new ReadTimeoutHandler(60))
                    .addHandlerLast(new WriteTimeoutHandler(60)));
        
        // Create the web client        
        WebClient webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .baseUrl(baseUrl)                               
            .build();
        return webClient;
    }

    private Mono<UserResponseFullBatchSuccessErrorDto> sendChunkToServerSuccessError(List<UserCallExternalRequestDTO> chunk, WebClient webClient, String uri, String currentUser) {
        log.info("Thread [{}] - Starting to send chunk of {} users to server", 
            Thread.currentThread().getName(), chunk.size());
            
        return webClient.post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Current-User", currentUser)
            .body(BodyInserters.fromValue(chunk))
            .exchangeToMono(response -> {
                log.info("Thread [{}] - Response status code: {}", Thread.currentThread().getName(), response.statusCode());
                
                return response.bodyToMono(UserResponseBatchSuccessErrorDto.class)
                    .map(body -> {
                        log.info("Thread [{}] - Successfully processed response", Thread.currentThread().getName());
                        return new UserResponseFullBatchSuccessErrorDto(
                            HttpStatus.valueOf(response.statusCode().value()),
                            body
                        );
                    })
                    .onErrorResume(e -> {
                        log.error("Error parsing response: {}", e.getMessage());
                        return Mono.just(new UserResponseFullBatchSuccessErrorDto(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            new UserResponseBatchSuccessErrorDto(0, 0, 0.0f, new ArrayList<>(), new ArrayList<>())
                        ));
                    });
            })
            .timeout(Duration.ofSeconds(200))
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
            .doOnError(e -> { log.error("Error: {}", e.getMessage()); })            
            .onErrorResume(ConnectException.class, ex -> {
                log.error("Connection error: {}", ex.getMessage());
                return Mono.just(new UserResponseFullBatchSuccessErrorDto(
                    HttpStatus.REQUEST_TIMEOUT,
                    new UserResponseBatchSuccessErrorDto(0, 0, 0.0f, new ArrayList<>(), new ArrayList<>())
                ));
            })
            .onErrorResume(throwable -> {
                if ((throwable instanceof TimeoutException) || 
                    (throwable instanceof AsyncRequestTimeoutException) ||
                    (throwable instanceof PrematureCloseException)) {
                    log.error("Timeout or premature close error: {}", throwable.getMessage());
                    return Mono.just(new UserResponseFullBatchSuccessErrorDto(
                        HttpStatus.REQUEST_TIMEOUT,
                        new UserResponseBatchSuccessErrorDto(0, 0, 0.0f, new ArrayList<>(), new ArrayList<>())
                    ));
                }
                return Mono.error(throwable);
            })
            .onErrorResume(WebClientResponseException.class, ex -> {
                log.error("WebClient error: {}", ex.getMessage());
                return Mono.just(new UserResponseFullBatchSuccessErrorDto(
                    HttpStatus.valueOf(ex.getStatusCode().value()),
                    new UserResponseBatchSuccessErrorDto(0, 1, 0.0f, new ArrayList<>(), new ArrayList<>())
                ));
            })
            .onErrorResume(Exception.class, ex -> {
                log.error("Unexpected error: {}", ex.getMessage());
                return Mono.just(new UserResponseFullBatchSuccessErrorDto(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    new UserResponseBatchSuccessErrorDto(0, 0, 0.0f, new ArrayList<>(), new ArrayList<>())
                ));
            })
            .doFinally(signalType -> 
                log.info("Thread [{}] - Request completed with signal: {}", 
                    Thread.currentThread().getName(), signalType));
    }

    private Mono<FullUserResponseDTO> sendChunkToServer(List<UserCallExternalRequestDTO> chunk, WebClient webClient, String currentUser) {
        String uri = "/api/users/batch_async";
        log.info("Thread [{}] - Starting to send chunk of {} users to server", 
            Thread.currentThread().getName(), chunk.size());
            
        return webClient.post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Current-User", currentUser)
            .body(BodyInserters.fromValue(chunk))
            .exchangeToMono(response -> {
                log.info("Thread [{}] - Response status code: {}", Thread.currentThread().getName(), response.statusCode());
                
                return response.bodyToMono(UserCallFullResponse.class)
                    .map(body -> {
                        log.info("Thread [{}] - Successfully processed response", Thread.currentThread().getName());
                        return new FullUserResponseDTO(HttpStatus.valueOf(response.statusCode().value()), body);
                    })
                    .onErrorResume(e -> {
                        log.error("Error parsing response: {}", e.getMessage());
                        return Mono.just(new FullUserResponseDTO(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            new UserCallFullResponse(0, 1, 0.0f, new ArrayList<>())
                        ));
                    });
            })
            .timeout(Duration.ofSeconds(10))
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                .filter(throwable -> {
                    boolean shouldRetry = throwable instanceof TimeoutException || 
                        throwable instanceof AsyncRequestTimeoutException ||
                        throwable instanceof PrematureCloseException ||
                        (throwable instanceof WebClientResponseException && 
                         ((WebClientResponseException) throwable).getStatusCode().is5xxServerError());
                    
                    if (shouldRetry) {
                        log.warn("Thread [{}] - Retrying request due to: {}", 
                            Thread.currentThread().getName(), throwable.getClass().getSimpleName());
                    }
                    return shouldRetry;
                })
                .doBeforeRetry(retrySignal -> 
                    log.info("Thread [{}] - Attempting retry {} of 3", 
                        Thread.currentThread().getName(), retrySignal.totalRetries() + 1)))
            .onErrorResume(ConnectException.class, ex -> {
                log.error("Connection error: {}", ex.getMessage());
                return Mono.just(new FullUserResponseDTO(
                    HttpStatus.REQUEST_TIMEOUT,
                    new UserCallFullResponse(0, 0, 0.0f, new ArrayList<>())
                ));
            })
            .onErrorResume(throwable -> {
                if ((throwable instanceof TimeoutException) || 
                    (throwable instanceof AsyncRequestTimeoutException) || 
                    (throwable instanceof PrematureCloseException)) {
                    log.error("Timeout or premature close error: {}", throwable.getMessage());
                    return Mono.just(new FullUserResponseDTO(
                        HttpStatus.REQUEST_TIMEOUT,
                        new UserCallFullResponse(0, 0, 0.0f, new ArrayList<>())
                    ));
                }
                return Mono.error(throwable);
            })
            .onErrorResume(WebClientResponseException.class, ex -> {
                log.error("WebClient error: {}", ex.getMessage());
                return Mono.just(new FullUserResponseDTO(
                    HttpStatus.valueOf(ex.getStatusCode().value()),
                    new UserCallFullResponse(0, 1, 0.0f, new ArrayList<>())
                ));
            })
            .onErrorResume(Exception.class, ex -> {
                log.error("Unexpected error: {}", ex.getMessage());
                return Mono.just(new FullUserResponseDTO(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    new UserCallFullResponse(0, 1, 0.0f, new ArrayList<>())
                ));
            })
            .doFinally(signalType -> 
                log.info("Thread [{}] - Request completed with signal: {}", 
                    Thread.currentThread().getName(), signalType));
    }

    private List<List<UserCallExternalRequestDTO>> createChunks(List<UserCallExternalRequestDTO> requestDTOs, int numberOfChunks) {
        int chunkSize = (int) Math.ceil((double)requestDTOs.size() / numberOfChunks);
        List<List<UserCallExternalRequestDTO>> chunks = new ArrayList<>();
        for (int i = 0; i < requestDTOs.size(); i += chunkSize) {
            chunks.add(requestDTOs.subList(i, Math.min(i + chunkSize, requestDTOs.size())));
        }
        return chunks;
    }

    // @Override
    // @Transactional
    // public Flux<FullUserResponseDTO> createUserReactiveNew(String currentUser, List<UserCallExternalRequestDTO> userCallExternalRequestDTOS) {
    //     List<List<UserCallExternalRequestDTO>> chunks = createChunks(userCallExternalRequestDTOS, 5);

    //     // Check if the current user has permission to create users
    //     return checkPermission(currentUser)
    //         .flatMapMany(permission -> {
    //             if (!permission) {
    //                 log.error("User does not have permission to create users");
    //                 return Flux.just(new FullUserResponseDTO(
    //                     HttpStatus.FORBIDDEN,
    //                     new UserCallFullResponse(0, 1, 0.0f, new ArrayList<>())
    //                 ));
    //             }
                
    //             return Flux.fromIterable(chunks)
    //                 .flatMap(chunk -> Mono.<FullUserResponseDTO>defer(() -> sendChunkToServer(chunk, webClient))
    //                 .publishOn(Schedulers.parallel()));
    //         })
    //         .onErrorResume(WebClientResponseException.class, ex -> {
    //             log.error("WebClient error: {}", ex.getMessage());
    //             return Flux.just(new FullUserResponseDTO(
    //                 HttpStatus.valueOf(ex.getStatusCode().value()),
    //                 new UserCallFullResponse(0, 1, 0.0f, new ArrayList<>())
    //             ));
    //         });
    // }

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