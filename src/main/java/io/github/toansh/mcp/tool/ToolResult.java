package io.github.toansh.mcp.tool;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

@RegisterForReflection
public record ToolResult(List<Content> content, boolean isError) {

    @RegisterForReflection
    public record Content(String type, String text) {
        public static Content text(String text) {
            return new Content("text", text);
        }
    }

    public static ToolResult ofText(String text) {
        return new ToolResult(List.of(Content.text(text)), false);
    }

    public static ToolResult error(String text) {
        return new ToolResult(List.of(Content.text(text)), true);
    }
}
