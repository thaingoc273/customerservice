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
public class UserDataGenerator {
    private final Faker faker = new Faker();

    public UserDTO generateUser() {
        return UserDTO.builder()
                .id(UUID.randomUUID().toString())
                .username(faker.name().username())
                .email(faker.internet().emailAddress())
                .password(faker.internet().password())
                .birthday(convertToLocalDate(faker.date().birthday()))
                .address(generateAddress())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .roles(generateRoles())
                .build();
    }

    public List<UserDTO> generateUsers(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> generateUser())
                .collect(Collectors.toList());
    }

    private Set<RoleDTO> generateRoles() {
        Set<RoleDTO> roles = new HashSet<>();
        // Add a random role
        roles.add(RoleDTO.builder()
                .id(UUID.randomUUID().toString())
                .roleCode(faker.options().option("USER", "ADMIN", "MANAGER"))
                .roleType("SYSTEM_ROLE")
                .build());
        return roles;
    }

    private String generateAddress() {
        return String.format("%s, %s, %s %s",
                faker.address().streetAddress(),
                faker.address().city(),
                faker.address().state(),
                faker.address().zipCode());
    }

    private LocalDate convertToLocalDate(java.util.Date date) {
        return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }
} 