package com.ecommerce.Customer.util;

import com.ecommerce.Customer.dto.UserCallExternalRequestDTO;
import com.github.javafaker.Faker;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

@Component
public class UserDataCallExternalApiGenerator {
  private final Faker faker = new Faker();

  public UserCallExternalRequestDTO generateUser() {
    return UserCallExternalRequestDTO.builder()
        .username(faker.name().username())
        .email(faker.internet().emailAddress())
        .password(faker.internet().password())
        .build();
  }

  public List<UserCallExternalRequestDTO> generateUsers(int count) {
    return IntStream.range(0, count).mapToObj(i -> generateUser()).collect(Collectors.toList());
  }
}
