package com.beehome.reading.dto;
import java.util.List;
public record ReadingPage<T>(List<T> items, int page, int size, boolean hasNext) {}
