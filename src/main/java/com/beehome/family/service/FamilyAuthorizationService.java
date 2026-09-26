package com.beehome.family.service;

import com.beehome.family.entity.FamilyRole;
import com.beehome.family.exception.FamilyException;
import com.beehome.family.repository.FamilyMembershipRepository;
import com.beehome.family.repository.FamilyRepository;
import com.beehome.user.service.UserService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FamilyAuthorizationService {
    private final FamilyMembershipRepository memberships;
    private final FamilyRepository families;
    private final UserService users;

    public FamilyAuthorizationService(FamilyMembershipRepository memberships, FamilyRepository families, UserService users) {
        this.memberships = memberships;
        this.families = families;
        this.users = users;
    }

    /** Call inside the media write transaction after checking membership. */
    public void lockFamily(UUID familyId) {
        families.lockById(familyId).orElseThrow(FamilyException::notFound);
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
