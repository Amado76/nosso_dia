package com.nossodia.family.service;

import com.nossodia.family.entity.FamilyRole;
import com.nossodia.family.exception.FamilyException;
import com.nossodia.family.repository.FamilyMembershipRepository;
import com.nossodia.user.service.UserService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FamilyAuthorizationService {
    private final FamilyMembershipRepository memberships;
    private final UserService users;

    public FamilyAuthorizationService(FamilyMembershipRepository memberships, UserService users) {
        this.memberships = memberships;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public FamilyRole requireMembership(UUID userId, UUID familyId) {
        users.current(userId);
        return memberships.findByFamilyIdAndUserId(familyId, userId)
                .orElseThrow(FamilyException::notFound).getRole();
    }

    public void requireEditor(FamilyRole role) {
        if (role == FamilyRole.MEMBER) throw FamilyException.forbidden();
    }
}
