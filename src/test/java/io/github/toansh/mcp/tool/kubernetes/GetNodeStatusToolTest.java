package io.github.toansh.mcp.tool.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.toansh.mcp.tool.ToolResult;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeCondition;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.NodeStatus;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NodeResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class GetNodeStatusToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private KubernetesClient client;
    private MixedOperation nodes;
    private NodeResource nodeResource;
    private NodeList nodeList;
    private GetNodeStatusTool tool;

    @BeforeEach
    void setUp() {
        client = mock(KubernetesClient.class);
        nodes = mock(MixedOperation.class);
        nodeResource = mock(NodeResource.class);
        nodeList = mock(NodeList.class);

        when(client.nodes()).thenReturn(nodes);
        when(nodes.list()).thenReturn(nodeList);
        when(nodes.withName(anyString())).thenReturn(nodeResource);

        tool = new GetNodeStatusTool();
        tool.mapper = MAPPER;
        tool.client = client;
    }

    @Test
    void getNodesSuccess() {
        Node n = new Node();
        ObjectMeta meta = new ObjectMeta();
        meta.setName("worker-1");
        n.setMetadata(meta);

        NodeStatus status = new NodeStatus();
        NodeCondition cond = new NodeCondition();
        cond.setType("Ready");
        cond.setStatus("True");
        status.setConditions(List.of(cond));
        n.setStatus(status);

        when(nodeList.getItems()).thenReturn(List.of(n));

        ObjectNode args = MAPPER.createObjectNode();
        ToolResult res = tool.call(args);

        assertFalse(res.isError());
        assertTrue(res.content().get(0).text().contains("worker-1"));
        assertTrue(res.content().get(0).text().contains("Ready: True"));
    }
}
