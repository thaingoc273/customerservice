## Spring Webflux
### How to setup
- A spring app using Webflux contains `asynchronous/concurrent` operations using `Schedulers.parallel()` or `publishOn()/subscribeOn()`.
- Using `Mono/Flux`
### Unit test for Webflux
- Using `StepVerifier` to test the concurrency
```java
class MyServiceTest {

    private MyService myService;

    @BeforeEach
    void setup() {
        myService = new MyService();
    }

    @Test
    void testProcessItems_withParallelScheduler() {
        List<String> input = List.of("a", "b", "c");

        Flux<String> resultFlux = myService.processItems(input);

        StepVerifier.create(resultFlux)
                .expectNextMatches(s -> List.of("A", "B", "C").contains(s)) // order not guaranteed
                .expectNextMatches(s -> List.of("A", "B", "C").contains(s))
                .expectNextMatches(s -> List.of("A", "B", "C").contains(s))
                .verifyComplete();
    }
}
```
- Unit test for multiple threads
```java
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private UserService userService;

    private CustomerService customerService;

    private final Set<String> threadNames = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(userService, new SimpleMeterRegistry());

        // Mock saveUser to track thread name
        Mockito.when(userService.saveUser(Mockito.any()))
            .thenAnswer(invocation -> {
                threadNames.add(Thread.currentThread().getName());
                return Mono.empty();
            });
    }

    @Test
    void shouldUseMultipleThreadsWhenProcessingChunks() {
        int totalUsers = 20;
        int chunkSize = 5;

        List<User> users = IntStream.range(0, totalUsers)
                .mapToObj(i -> new User("User-" + i))
                .toList();

        customerService.saveUsersInChunks(users, chunkSize).block();

        // Assert number of threads used
        threadNames.forEach(System.out::println);
        assertTrue(threadNames.size() > 1, "Expected more than one thread to be used");
    }
}

```
### Tips for reliable Unit Testing in Webflux
1. Avoid blocking call like `Thread.sleep()` or `.block()` in production code. Use `VirtualTimeScheduler` for time-based tests.
- Use reactive libraries
    - `R2DBC` instead of `JDBC`
    - `WebClient` instead of `RestTemplate`
    - `Redis Reactive` instead of `Jedis`
2. Avoid `.subcribe()` in production methods unless it is neccessary

3. For side-effects (e.g. database, messaging), use ``flatMap()`` with relative clients and mock them in tests