package com.microinvent.identity.repository;

import com.microinvent.identity.domain.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Integer id);

    /**
     * Danh sách người dùng cho admin. Tham số null = không lọc; keyword đã chuẩn hóa dạng "%abc%" (chữ thường).
     */
    @EntityGraph(attributePaths = {"role"})
    @Query(value = """
            select u from User u
            where (:keyword is null or lower(u.username) like :keyword escape '!' or lower(u.fullName) like :keyword escape '!'
                   or lower(u.email) like :keyword escape '!' or u.phone like :keyword escape '!')
              and (:role is null or u.role.code = :role)
              and (:active is null or u.active = :active)
              and (:warehouseId is null or exists (
                   select 1 from UserWarehouse w where w.user = u and w.id.warehouseId = :warehouseId))
            """,
            countQuery = """
            select count(u) from User u
            where (:keyword is null or lower(u.username) like :keyword escape '!' or lower(u.fullName) like :keyword escape '!'
                   or lower(u.email) like :keyword escape '!' or u.phone like :keyword escape '!')
              and (:role is null or u.role.code = :role)
              and (:active is null or u.active = :active)
              and (:warehouseId is null or exists (
                   select 1 from UserWarehouse w where w.user = u and w.id.warehouseId = :warehouseId))
            """)
    Page<User> search(String keyword, String role, Boolean active, Integer warehouseId, Pageable pageable);
}
