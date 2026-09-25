package com.beehome.familymember.service;

import com.beehome.family.entity.FamilyRole;
import com.beehome.family.exception.FamilyException;
import com.beehome.family.service.FamilyAuthorizationService;
import com.beehome.familymember.dto.CreateFamilyMemberRequest;
import com.beehome.familymember.dto.FamilyMemberResponse;
import com.beehome.familymember.dto.PatchFamilyMemberRequest;
import com.beehome.familymember.entity.FamilyMember;
import com.beehome.familymember.entity.MemberType;
import com.beehome.familymember.exception.FamilyMemberException;
import com.beehome.familymember.repository.FamilyMemberRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import com.beehome.familymember.dto.FamilyMemberPage;
import org.springframework.data.domain.PageRequest;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FamilyMemberService {
    private final FamilyMemberRepository members;
    private final FamilyAuthorizationService authorization;
    private final Clock clock;

    public FamilyMemberService(FamilyMemberRepository members, FamilyAuthorizationService authorization, Clock clock) {
        this.members = members;
        this.authorization = authorization;
        this.clock = clock;
    }

    /** Family-scoped access used by planning; writes serialize with member deactivation. */
    @Transactional
    public FamilyMemberResponse requireActive(UUID userId, UUID familyId, UUID memberId, boolean forWrite) {
        authorization.requireMembership(userId, familyId);
        var member = (forWrite ? members.lockByFamilyIdAndId(familyId, memberId)
                : members.findByFamilyIdAndId(familyId, memberId)).orElseThrow(FamilyMemberException::notFound);
        if (!member.isActive()) throw FamilyMemberException.notFound();
        return FamilyMemberResponse.from(member);
    }

    @Transactional
    public FamilyMemberResponse create(UUID userId, UUID familyId, CreateFamilyMemberRequest request) {
        authorization.requireEditor(authorization.requireMembership(userId, familyId));
        return FamilyMemberResponse.from(members.save(new FamilyMember(familyId, request.name(), request.memberType(),
                request.birthDate(), request.color(), LocalDate.now(clock.withZone(ZoneOffset.UTC)), clock.instant())));
    }

    @Transactional(readOnly = true)
    public FamilyMemberPage list(UUID userId, UUID familyId, String includeInactive, String type, int page, int size) {
        authorization.requireMembership(userId, familyId);
        if (includeInactive != null && !includeInactive.equals("true") && !includeInactive.equals("false")) {
            throw FamilyMemberException.invalid("filters");
        }
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE - 1) {
            throw FamilyException.invalidPage();
        }
        MemberType filter = null;
        if (type != null) {
            try { filter = MemberType.valueOf(type); }
            catch (IllegalArgumentException exception) { throw FamilyMemberException.invalid("filters"); }
        }
        var slice = members.list(familyId, "true".equals(includeInactive), filter, PageRequest.of(page, size));
        return new FamilyMemberPage(slice.getContent().stream().map(FamilyMemberResponse::from).toList(),
                page, size, slice.hasNext());
    }

    @Transactional(readOnly = true)
    public FamilyMemberResponse get(UUID userId, UUID familyId, UUID memberId) {
        authorization.requireMembership(userId, familyId);
        return FamilyMemberResponse.from(members.findByFamilyIdAndId(familyId, memberId).orElseThrow(FamilyMemberException::notFound));
    }

    @Transactional
    public FamilyMemberResponse edit(UUID userId, UUID familyId, UUID memberId, PatchFamilyMemberRequest request) {
        var role = authorization.requireMembership(userId, familyId);
        var member = locked(familyId, memberId);
        authorization.requireEditor(role);
        request.validatePresence();
        var fields = request.fields();
        if (fields.contains("preferences")) member.patchPreferences(request.preferences(), clock.instant());
        member.edit(fields.contains("name") ? request.name() : member.getName(),
                fields.contains("memberType") ? request.memberType() : member.getMemberType(),
                fields.contains("birthDate") ? request.birthDate() : member.getBirthDate(),
                fields.contains("color") ? request.color() : member.getColor(),
                LocalDate.now(clock.withZone(ZoneOffset.UTC)), clock.instant());
        return FamilyMemberResponse.from(member);
    }

    @Transactional
    public FamilyMemberResponse setActive(UUID userId, UUID familyId, UUID memberId, boolean active) {
        var role = authorization.requireMembership(userId, familyId);
        var member = locked(familyId, memberId);
        authorization.requireEditor(role);
        member.setActive(active, clock.instant());
        return FamilyMemberResponse.from(member);
    }

    @Transactional
    public FamilyMemberResponse linkMe(UUID userId, UUID familyId, UUID memberId) {
        authorization.requireMembership(userId, familyId);
        var member = locked(familyId, memberId);
        linkAccount(member, userId);
        return FamilyMemberResponse.from(member);
    }

    // Keep target membership mandatory when this operation is reused for future account linking.
    private void linkAccount(FamilyMember member, UUID targetUserId) {
        authorization.requireMembership(targetUserId, member.getFamilyId());
        if (targetUserId.equals(member.getLinkedUserId())) return;
        if (member.getLinkedUserId() != null) throw FamilyMemberException.alreadyLinked();
        if (!member.isActive()) throw FamilyMemberException.inactive();
        if (members.existsByFamilyIdAndLinkedUserId(member.getFamilyId(), targetUserId)) {
            throw FamilyMemberException.userAlreadyLinked();
        }
        member.link(targetUserId, clock.instant());
        try {
            members.flush();
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint) {
                    if ("family_members_family_user_unique".equals(constraint.getConstraintName())) {
                        throw FamilyMemberException.userAlreadyLinked();
                    }
                    if ("family_members_membership_fk".equals(constraint.getConstraintName())) {
                        throw FamilyException.notFound();
                    }
                }
            }
            throw exception;
        }
    }

    @Transactional
    public void unlink(UUID userId, UUID familyId, UUID memberId) {
        var role = authorization.requireMembership(userId, familyId);
        var member = locked(familyId, memberId);
        if (role == FamilyRole.MEMBER && !userId.equals(member.getLinkedUserId())) throw FamilyException.forbidden();
        member.unlink(clock.instant());
    }

    // All writes lock the row so profile/state updates cannot overwrite a concurrent account link.
    private FamilyMember locked(UUID familyId, UUID memberId) {
        return members.lockByFamilyIdAndId(familyId, memberId).orElseThrow(FamilyMemberException::notFound);
    }
}
