package com.beehome.media.dto;

import java.util.List;
public record MediaPage(List<MediaResponse> items, int page, int size, boolean hasNext) {}
