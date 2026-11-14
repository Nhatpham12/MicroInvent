package com.microinvent.identity.repository;

import com.microinvent.identity.domain.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoleRepository extends JpaRepository<Role, Integer> {

    Optional<Role> findByCode(String code);

    @EntityGraph(attributePaths = {"permissions"})
    @Query("select r from Role r order by r.id")
    List<Role> findAllWithPermissions();
}
