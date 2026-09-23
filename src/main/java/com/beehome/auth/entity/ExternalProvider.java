package com.beehome.auth.entity;

public enum ExternalProvider {
    GOOGLE("https://accounts.google.com"),
    APPLE("https://appleid.apple.com");

    private final String issuer;
    ExternalProvider(String issuer) { this.issuer = issuer; }
    public String issuer() { return issuer; }
}
