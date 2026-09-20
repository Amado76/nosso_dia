package com.nossodia.shared.exception;

public record ValidationError(String field, String message) {}
