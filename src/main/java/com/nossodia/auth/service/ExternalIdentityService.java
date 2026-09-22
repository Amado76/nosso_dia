package com.nossodia.auth.service;

import com.nossodia.auth.entity.ExternalProvider;
import com.nossodia.auth.exception.AuthException;
import com.nossodia.auth.repository.ExternalIdentityRepository;

import com.nossodia.user.UserService;
import com.nossodia.user.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Accepts an identity already verified by Spring Security's OIDC client, never a client-supplied subject. */
@Service
public class ExternalIdentityService {
    private final ExternalIdentityRepository identities;
    private final UserService users;
    public ExternalIdentityService(ExternalIdentityRepository identities, UserService users) {
        this.identities = identities; this.users = users;
    }
    @Transactional(readOnly = true)
    public UserResponse resolve(ExternalProvider provider, OidcUser verifiedIdentity) {
        if (verifiedIdentity.getIssuer() == null || !provider.issuer().equals(verifiedIdentity.getIssuer().toString()))
            throw new AuthException("INVALID_CREDENTIALS", "error.auth.invalid-credentials");
        var identity = identities.findByProviderAndProviderSubject(provider, verifiedIdentity.getSubject())
                .orElseThrow(() -> new AuthException(HttpStatus.CONFLICT,
                        "EXTERNAL_IDENTITY_NOT_LINKED", "error.auth.external-identity-not-linked"));
        // Email is intentionally not used for lookup, creation, or automatic account linking.
        return users.current(identity.getUserId());
    }
}
