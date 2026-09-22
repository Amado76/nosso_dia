package com.nossodia.auth.repository;

import com.nossodia.auth.entity.ExternalIdentity;
import com.nossodia.auth.entity.ExternalProvider;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, UUID> {
    Optional<ExternalIdentity> findByProviderAndProviderSubject(ExternalProvider provider, String subject);
}
