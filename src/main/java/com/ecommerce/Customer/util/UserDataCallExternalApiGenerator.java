package com.ecommerce.Customer.util;

import com.ecommerce.Customer.dto.RoleDTO;
import com.ecommerce.Customer.dto.UserCallExternalRequestDTO;
import com.ecommerce.Customer.dto.UserDTO;
import com.github.javafaker.Faker;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        return IntStream.range(0, count)
                .mapToObj(i -> generateUser())
                .collect(Collectors.toList());
    } 
} 