package com.analyzer.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.analyzer.api.entity.User;
import com.analyzer.api.enums.RoleName;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    boolean existsByEmail(String email);

    Optional<User> findByEmailVerificationToken(String emailVerificationToken);

    Optional<User> findByEmailVerificationLastUsedToken(String emailVerificationLastUsedToken);

    Optional<User> findByForgotPasswordToken(String forgotPasswordToken);

    List<User> findAllByRole_NameAndActiveTrue(RoleName roleName);

    List<User> findAllByMustChangePasswordTrueAndActiveTrueAndPasswordResetDeadlineBefore(LocalDateTime now);
}
