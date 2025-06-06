package com.ecommerce.Customer.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "roles")
public class Role {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
        name = "UUID",
        strategy = "org.hibernate.id.UUIDGenerator"
    )
    @Column(length = 36, updatable = false)
    private String id;

    @NotBlank(message = "Role code is required")
    @Size(min = 2, max = 50, message = "Role code must be between 2 and 50 characters")
    @Column(name = "role_code", length = 50, nullable = false, unique = true)
    private String roleCode;

    @NotBlank(message = "Role type is required")
    @Size(min = 2, max = 50, message = "Role type must be between 2 and 50 characters")
    @Column(name = "role_type", length = 50, nullable = false)
    private String roleType;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder.Default
    @ManyToMany(mappedBy = "roles")
    private Set<User> users = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
} 