package com.nossodia.family.service;

import com.nossodia.family.entity.Family;
import com.nossodia.family.entity.FamilyMembership;
import com.nossodia.family.entity.FamilyRole;
import com.nossodia.family.exception.FamilyException;
import com.nossodia.family.repository.FamilyMembershipRepository;
import com.nossodia.family.repository.FamilyRepository;

import com.nossodia.family.dto.FamilyPage;
import com.nossodia.family.dto.FamilyResponse;
import com.nossodia.user.service.UserService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FamilyService {
    private final FamilyRepository families;
    private final FamilyMembershipRepository memberships;
    private final UserService users;
    private final Clock clock;
    private final FamilyAuthorizationService authorization;

    public FamilyService(FamilyRepository families, FamilyMembershipRepository memberships,
            UserService users, Clock clock, FamilyAuthorizationService authorization) {
        this.families = families;
        this.memberships = memberships;
        this.users = users;
        this.clock = clock;
        this.authorization = authorization;
    }

    @Transactional
    public FamilyResponse create(UUID userId, String name) {
        users.current(userId);
        var now = clock.instant();
        var family = families.save(new Family(name, now));
        memberships.save(new FamilyMembership(family.getId(), userId, FamilyRole.OWNER, now));
        return response(family, FamilyRole.OWNER);
    }

    @Transactional(readOnly = true)
    public FamilyPage list(UUID userId, int page, int size) {
        users.current(userId);
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE - 1) {
            throw FamilyException.invalidPage();
        }
        var slice = families.findAccessible(userId, PageRequest.of(page, size));
        return new FamilyPage(slice.getContent(), page, size, slice.hasNext());
    }

    @Transactional(readOnly = true)
    public FamilyResponse get(UUID userId, UUID familyId) {
        users.current(userId);
        return families.findAccessible(userId, familyId).orElseThrow(FamilyException::notFound);
    }

    @Transactional
    public FamilyResponse rename(UUID userId, UUID familyId, String name) {
        var role = authorization.requireMembership(userId, familyId);
        authorization.requireEditor(role);
        var family = families.findById(familyId).orElseThrow(FamilyException::notFound);
        family.rename(name, clock.instant());
        return response(family, role);
    }

    private static FamilyResponse response(Family family, FamilyRole role) {
        return new FamilyResponse(family.getId(), family.getName(), role, family.getCreatedAt(), family.getUpdatedAt());
    }
}
