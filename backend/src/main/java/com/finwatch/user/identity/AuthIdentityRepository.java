package com.finwatch.user.identity;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<AuthIdentity> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);
}
