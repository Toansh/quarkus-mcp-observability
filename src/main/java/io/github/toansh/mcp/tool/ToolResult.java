package io.github.toansh.mcp.tool;

import java.util.List;

public record ToolResult(List<Content> content, boolean isError) {

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
