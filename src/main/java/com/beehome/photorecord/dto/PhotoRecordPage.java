package com.beehome.photorecord.dto;

import java.util.List;
public record PhotoRecordPage(List<PhotoRecordResponse> items, int page, int size, boolean hasNext) {}
