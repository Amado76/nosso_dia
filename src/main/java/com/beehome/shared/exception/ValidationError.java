package com.beehome.shared.exception;

public record ValidationError(String field, String message) {}
