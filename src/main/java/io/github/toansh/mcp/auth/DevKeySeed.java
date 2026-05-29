package io.github.toansh.mcp.auth;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * Dev-mode only: seeds a fixed, known API key if {@code api_keys} is empty so the demo path
 * (curl /mcp with Bearer header) works out-of-the-box. Prod operators insert keys via SQL —
 * never via auto-seed.
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class DevKeySeed {

    private static final Logger LOG = Logger.getLogger(DevKeySeed.class);

    static final String DEV_TOKEN = "mcp_dev_local_do_not_use_in_prod";
    static final String DEV_PRINCIPAL = "dev-local";

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        if (ApiKey.count() > 0) {
            return;
        }
        ApiKey key = new ApiKey();
        key.keyHash = ApiKeys.sha256Hex(DEV_TOKEN);
        key.principal = DEV_PRINCIPAL;
        key.label = "dev-seed";
        key.createdAt = Instant.now();
        key.revoked = false;
        key.persist();

        LOG.infof("Seeded dev API key. Authenticate with: Authorization: Bearer %s", DEV_TOKEN);
    }
}
