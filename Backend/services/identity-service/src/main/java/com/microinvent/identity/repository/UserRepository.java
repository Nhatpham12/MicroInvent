package com.microinvent.identity.repository;

import com.microinvent.identity.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Integer> {

    @EntityGraph(attributePaths = {"role"})
    @Query("select u from User u where lower(u.username) = lower(:login) or lower(u.email) = lower(:login)")
    Optional<User> findByLogin(String login);

    @EntityGraph(attributePaths = {"role", "role.permissions", "warehouses"})
    @Query("select u from User u where u.id = :id")
    Optional<User> findWithScopeById(Integer id);
}
