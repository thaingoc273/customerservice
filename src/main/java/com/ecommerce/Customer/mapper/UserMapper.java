package com.ecommerce.Customer.mapper;

import com.ecommerce.Customer.dto.RoleDTO;
import com.ecommerce.Customer.dto.UserCallExternalRequestDTO;
import com.ecommerce.Customer.dto.UserDTO;
import com.ecommerce.Customer.entity.Role;
import com.ecommerce.Customer.entity.User;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

  public UserDTO toDTO(User user) {
    if (user == null) {
      return null;
    }

    return UserDTO.builder()
        .id(user.getId())
        .username(user.getUsername())
        .password(user.getPassword())
        .email(user.getEmail())
        .birthday(user.getBirthday())
        .address(user.getAddress())
        .createdAt(user.getCreatedAt())
        .updatedAt(user.getUpdatedAt())
        .roles(mapRoles(user.getRoles()))
        .build();
  }

  private Set<RoleDTO> mapRoles(Set<Role> roles) {
    if (roles == null) {
      return null;
    }

    return roles.stream()
        .map(
            role ->
                RoleDTO.builder()
                    .id(role.getId())
                    .roleCode(role.getRoleCode())
                    .roleType(role.getRoleType())
                    .build())
        .collect(Collectors.toSet());
  }

  public User toEntityfromUserDTO(UserDTO userDTO) {
    if (userDTO == null) {
      return null;
    }

    return User.builder()
        .id(userDTO.getId())
        .username(userDTO.getUsername())
        .password(userDTO.getPassword())
        .email(userDTO.getEmail())
        .birthday(userDTO.getBirthday())
        .address(userDTO.getAddress())
        .createdAt(userDTO.getCreatedAt())
        .updatedAt(userDTO.getUpdatedAt())
        .roles(mapRoleDTOsToRoles(userDTO.getRoles()))
        .build();
  }

  public User toEntityfromUserCallExternalDTO(UserCallExternalRequestDTO userCallExternalDTO) {
    if (userCallExternalDTO == null) {
      return null;
    }

    return User.builder()
        .username(userCallExternalDTO.getUsername())
        .password(userCallExternalDTO.getPassword())
        .email(userCallExternalDTO.getEmail())
        .build();
  }

  private Set<Role> mapRoleDTOsToRoles(Set<RoleDTO> roleDTOs) {
    if (roleDTOs == null) {
      return null;
    }

    return roleDTOs.stream()
        .map(
            roleDTO ->
                Role.builder()
                    .id(roleDTO.getId())
                    .roleCode(roleDTO.getRoleCode())
                    .roleType(roleDTO.getRoleType())
                    .build())
        .collect(Collectors.toSet());
  }
}
