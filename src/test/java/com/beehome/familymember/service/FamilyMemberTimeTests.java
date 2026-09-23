package com.beehome.familymember.service;

import com.beehome.family.entity.FamilyRole;
import com.beehome.family.service.FamilyAuthorizationService;
import com.beehome.familymember.dto.CreateFamilyMemberRequest;
import com.beehome.familymember.entity.MemberType;
import com.beehome.familymember.exception.FamilyMemberException;
import com.beehome.familymember.repository.FamilyMemberRepository;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FamilyMemberTimeTests {
    @Test
    void validatesTodayInUtcEvenWhenInjectedClockUsesAnotherZone() {
        var clock = Clock.fixed(Instant.parse("2026-01-02T01:00:00Z"), ZoneId.of("America/Asuncion"));
        var repository = mock(FamilyMemberRepository.class);
        var authorization = mock(FamilyAuthorizationService.class);
        UUID user = UUID.randomUUID(), family = UUID.randomUUID();
        when(authorization.requireMembership(user, family)).thenReturn(FamilyRole.OWNER);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new FamilyMemberService(repository, authorization, clock);
        var result = service.create(user, family, new CreateFamilyMemberRequest("Person", MemberType.ADULT, LocalDate.of(2026, 1, 2), null));
        assertThat(result.birthDate()).isEqualTo(LocalDate.of(2026, 1, 2));
        assertThat(result.memberType()).isEqualTo(MemberType.ADULT);
        assertThat(result.createdAt()).isEqualTo(clock.instant());
        assertThatThrownBy(() -> service.create(user, family,
                new CreateFamilyMemberRequest("Person", MemberType.CHILD, LocalDate.of(2026, 1, 3), null)))
                .isInstanceOf(FamilyMemberException.class).extracting("code").isEqualTo("VALIDATION_ERROR");
    }
}
