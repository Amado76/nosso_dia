package com.beehome.family.service;

import com.beehome.family.entity.Family;
import com.beehome.family.entity.FamilyMembership;
import com.beehome.family.entity.FamilyRole;
import com.beehome.family.exception.FamilyException;
import com.beehome.family.repository.FamilyMembershipRepository;
import com.beehome.family.repository.FamilyRepository;

import com.beehome.family.dto.FamilyPage;
import com.beehome.family.dto.FamilyResponse;
import com.beehome.user.service.UserService;
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
    public FamilyResponse create(UUID userId, String name, String timezone) {
        users.current(userId);
        var now = clock.instant();
        var family = families.save(new Family(name, timezone, now));
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
    public void lockForWrite(UUID userId, UUID familyId) {
        authorization.requireEditor(authorization.requireMembership(userId, familyId));
        // The caller's transaction retains this lock for family-scoped allocation limits.
        families.lockById(familyId).orElseThrow(FamilyException::notFound);
    }

    @Transactional
    public FamilyResponse edit(UUID userId, UUID familyId, com.beehome.family.dto.PatchFamilyRequest request) {
        var role = authorization.requireMembership(userId, familyId);
        authorization.requireEditor(role);
        var family = families.lockById(familyId).orElseThrow(FamilyException::notFound);
        if (request.fields().isEmpty()) throw new com.beehome.shared.exception.InputException();
        family.edit(request.fields().contains("name") ? request.name() : family.getName(),
                request.fields().contains("timezone") ? request.timezone() : family.getTimezone(), clock.instant());
        return response(family, role);
    }

    private static FamilyResponse response(Family family, FamilyRole role) {
        return new FamilyResponse(family.getId(), family.getName(), family.getTimezone(), role, family.getCreatedAt(), family.getUpdatedAt());
    }
}
