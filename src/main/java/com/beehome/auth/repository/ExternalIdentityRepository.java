package com.beehome.auth.repository;

import com.beehome.auth.entity.ExternalIdentity;
import com.beehome.auth.entity.ExternalProvider;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, UUID> {
    Optional<ExternalIdentity> findByProviderAndProviderSubject(ExternalProvider provider, String subject);
}
