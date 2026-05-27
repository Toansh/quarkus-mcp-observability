package io.github.toansh.mcp.tool;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class ToolRegistry {

    @Inject
    Instance<Tool> tools;

    private Map<String, Tool> byName;

    @PostConstruct
    void init() {
        Map<String, Tool> m = new LinkedHashMap<>();
        for (Tool t : tools) {
            m.put(t.name(), t);
        }
        this.byName = Map.copyOf(m);
    }

    public Optional<Tool> get(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public Collection<Tool> all() {
        return byName.values();
    }
}
