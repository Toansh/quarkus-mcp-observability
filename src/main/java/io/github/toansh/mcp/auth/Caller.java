package io.github.toansh.mcp.auth;

import jakarta.enterprise.context.RequestScoped;

/**
 * Holds the authenticated principal for the current request. Populated by {@link ApiKeyAuthFilter}
 * before the JAX-RS resource method runs, then injected into {@code McpResource}.
 */
@RequestScoped
public class Caller {

    private String principal;

    public String getPrincipal() {
        return principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }
}
