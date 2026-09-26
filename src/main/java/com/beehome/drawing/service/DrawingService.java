package com.beehome.drawing.service;

import com.beehome.drawing.dto.*;
import com.beehome.drawing.entity.Drawing;
import com.beehome.drawing.exception.DrawingException;
import com.beehome.drawing.repository.DrawingRepository;
import com.beehome.family.service.FamilyAuthorizationService;
import com.beehome.familymember.service.FamilyMemberService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DrawingService {
    private static final String PROFILE = "PROFILE_SCRATCHPAD";
    private final DrawingRepository drawings;
    private final FamilyMemberService members;
    private final FamilyAuthorizationService authorization;
    private final Clock clock;
    private final DrawingDocumentValidator validator;

    public DrawingService(DrawingRepository drawings, FamilyMemberService members,
            FamilyAuthorizationService authorization, Clock clock, DrawingDocumentValidator validator) {
        this.drawings = drawings;
        this.members = members;
        this.authorization = authorization;
        this.clock = clock;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public DrawingResponse get(UUID userId, UUID familyId, UUID memberId) {
        members.get(userId, familyId, memberId);
        return drawings.findByFamilyIdAndMemberIdAndSurface(familyId, memberId, PROFILE)
                .map(this::response).orElseGet(() -> new DrawingResponse(familyId, memberId, PROFILE, 1, 0,
                        List.of(), null, null));
    }

    @Transactional
    public DrawingResponse replace(UUID userId, UUID familyId, UUID memberId, byte[] request) {
        authorization.requireEditor(members.lockForDependentWrite(userId, familyId, memberId));
        DrawingUpdate update = validator.parse(request);
        var existing = drawings.findByFamilyIdAndMemberIdAndSurface(familyId, memberId, PROFILE);
        long currentRevision = existing.map(Drawing::getRevision).orElse(0L);
        if (currentRevision != update.revision() || currentRevision == Long.MAX_VALUE) throw DrawingException.conflict();
        var document = new DrawingDocument(update.formatVersion(), update.strokes());
        Drawing drawing;
        if (existing.isPresent()) {
            drawing = existing.get();
            drawing.replace(document, clock.instant());
        } else {
            drawing = drawings.save(new Drawing(familyId, memberId, PROFILE, document, clock.instant()));
        }
        return response(drawing);
    }

    private DrawingResponse response(Drawing drawing) {
        return new DrawingResponse(drawing.getFamilyId(), drawing.getMemberId(), drawing.getSurface(),
                drawing.getDocument().formatVersion(), drawing.getRevision(), drawing.getDocument().strokes(),
                drawing.getCreatedAt(), drawing.getUpdatedAt());
    }
}
