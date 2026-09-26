package com.beehome.activity.dto;

import java.util.List;

public record ActivityRecordPage(List<ActivityRecordResponse> items, int page, int size, boolean hasNext) {}
