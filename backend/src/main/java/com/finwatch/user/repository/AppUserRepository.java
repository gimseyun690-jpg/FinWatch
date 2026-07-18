package com.finwatch.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.finwatch.user.domain.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
