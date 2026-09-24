package com.beehome.study.dto;
import java.util.List;
public record StudySessionPage(List<StudySessionResponse> items, int page, int size, boolean hasNext) {}
