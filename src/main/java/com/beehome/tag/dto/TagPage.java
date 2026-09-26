package com.beehome.tag.dto;

import java.util.List;

public record TagPage(List<TagResponse> items, int page, int size, boolean hasNext) {}
