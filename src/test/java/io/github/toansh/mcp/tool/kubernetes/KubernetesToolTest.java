package io.github.toansh.mcp.tool.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.toansh.mcp.tool.ToolResult;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.AppsAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class KubernetesToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private KubernetesClient client;
    
    private MixedOperation pods;
    private NonNamespaceOperation podsInNamespace;
    private PodResource podResource;
    
    private AppsAPIGroupDSL apps;
    private MixedOperation deployments;
    private NonNamespaceOperation deploymentsInNamespace;
    private RollableScalableResource deploymentResource;

    private GetPodLogsTool logsTool;
    private DescribeDeploymentTool describeTool;

    @BeforeEach
    void setUp() {
        client = mock(KubernetesClient.class);
        pods = mock(MixedOperation.class);
        podsInNamespace = mock(NonNamespaceOperation.class);
        podResource = mock(PodResource.class);
        
        apps = mock(AppsAPIGroupDSL.class);
        deployments = mock(MixedOperation.class);
        deploymentsInNamespace = mock(NonNamespaceOperation.class);
        deploymentResource = mock(RollableScalableResource.class);

        when(client.getNamespace()).thenReturn("default");
        
        // Pods chain
        when(client.pods()).thenReturn(pods);
        when(pods.inNamespace(anyString())).thenReturn(podsInNamespace);
        when(podsInNamespace.withName(anyString())).thenReturn(podResource);
        when(podResource.tailingLines(anyInt())).thenReturn(podResource);
        when(podResource.inContainer(anyString())).thenReturn(podResource);

        // Deployments chain
        when(client.apps()).thenReturn(apps);
        when(apps.deployments()).thenReturn(deployments);
        when(deployments.inNamespace(anyString())).thenReturn(deploymentsInNamespace);
        when(deploymentsInNamespace.withName(anyString())).thenReturn(deploymentResource);

        logsTool = new GetPodLogsTool();
        logsTool.mapper = MAPPER;
        logsTool.client = client;

        describeTool = new DescribeDeploymentTool();
        describeTool.mapper = MAPPER;
        describeTool.client = client;
    }

    @Test
    void getPodLogsSuccess() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("podName", "my-pod");
        
        when(podResource.getLog()).thenReturn("line 1\nline 2");

        ToolResult result = logsTool.call(args);
        assertFalse(result.isError());
        assertEquals("line 1\nline 2", result.content().get(0).text());
    }

    @Test
    void getPodLogsWithNamespaceAndContainer() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("podName", "my-pod");
        args.put("namespace", "other");
        args.put("containerName", "sidecar");
        
        when(podResource.getLog()).thenReturn("sidecar logs");

        ToolResult result = logsTool.call(args);
        assertFalse(result.isError());
        assertEquals("sidecar logs", result.content().get(0).text());
    }

    @Test
    void getPodLogsError() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("podName", "non-existent");
        
        when(podResource.getLog()).thenThrow(new RuntimeException("Pod not found"));

        ToolResult result = logsTool.call(args);
        assertTrue(result.isError());
        assertTrue(result.content().get(0).text().contains("Pod not found"));
    }

    @Test
    void describeDeploymentSuccess() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("name", "my-deploy");
        
        Deployment deployment = new Deployment();
        ObjectMeta meta = new ObjectMeta();
        meta.setName("my-deploy");
        deployment.setMetadata(meta);
        
        when(deploymentResource.get()).thenReturn(deployment);

        ToolResult result = describeTool.call(args);
        assertFalse(result.isError());
        assertTrue(result.content().get(0).text().contains("my-deploy"));
    }

    @Test
    void describeDeploymentNotFound() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("name", "missing");
        
        when(deploymentResource.get()).thenReturn(null);

        ToolResult result = describeTool.call(args);
        assertTrue(result.isError());
        assertTrue(result.content().get(0).text().contains("not found"));
    }
}
