package io.github.toansh.mcp.tool.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.toansh.mcp.tool.ToolResult;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.V1APIGroupDSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class GetClusterEventsToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private KubernetesClient client;
    private V1APIGroupDSL v1;
    private MixedOperation events;
    private NonNamespaceOperation eventsInNamespace;
    private EventList eventList;
    private GetClusterEventsTool tool;

    @BeforeEach
    void setUp() {
        client = mock(KubernetesClient.class);
        v1 = mock(V1APIGroupDSL.class);
        events = mock(MixedOperation.class);
        eventsInNamespace = mock(NonNamespaceOperation.class);
        eventList = mock(EventList.class);

        when(client.v1()).thenReturn(v1);
        when(v1.events()).thenReturn(events);
        when(events.inAnyNamespace()).thenReturn(eventsInNamespace);
        when(events.inNamespace(anyString())).thenReturn(eventsInNamespace);
        when(eventsInNamespace.list()).thenReturn(eventList);
        when(events.list()).thenReturn(eventList);

        tool = new GetClusterEventsTool();
        tool.mapper = MAPPER;
        tool.client = client;
    }

    @Test
    void getEventsSuccess() {
        Event e = new Event();
        e.setType("Warning");
        e.setReason("BackOff");
        e.setMessage("Back-off pulling image");
        e.setCount(5);
        when(eventList.getItems()).thenReturn(List.of(e));

        ObjectNode args = MAPPER.createObjectNode();
        ToolResult res = tool.call(args);

        assertFalse(res.isError());
        assertTrue(res.content().get(0).text().contains("BackOff"));
        assertTrue(res.content().get(0).text().contains("Back-off pulling image"));
    }

    @Test
    void getEventsNoMatch() {
        when(eventList.getItems()).thenReturn(List.of());

        ObjectNode args = MAPPER.createObjectNode();
        ToolResult res = tool.call(args);

        assertFalse(res.isError());
        assertTrue(res.content().get(0).text().contains("no Kubernetes events found"));
    }
}
