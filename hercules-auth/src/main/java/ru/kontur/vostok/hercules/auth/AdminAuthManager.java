package ru.kontur.vostok.hercules.auth;

import java.util.Set;

/**
 * @author Gregory Koshelev
 */
public class AdminAuthManager {
    private final Set<String> adminKeys;

    public AdminAuthManager(Set<String> adminKeys) {
        this.adminKeys = adminKeys;
    }

    public AuthResult auth(String apiKey) {
        return adminKeys.contains(apiKey) ? AuthResult.ok() : AuthResult.denied();
    }
}
