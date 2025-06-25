package com.ecommerce.Customer.repository;

import com.ecommerce.Customer.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

  @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles")
  List<User> findAllUsersWithRoles();
}
