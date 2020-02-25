package test.com.controller;

import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.apache.nifi.registry.bucket.Bucket;
import org.apache.nifi.registry.client.NiFiRegistryClient;
import org.apache.nifi.registry.client.NiFiRegistryClientConfig;
import org.apache.nifi.registry.client.NiFiRegistryException;
import org.apache.nifi.registry.client.impl.JerseyNiFiRegistryClient;
import org.apache.nifi.registry.flow.VersionedFlow;
import org.apache.nifi.registry.flow.VersionedFlowSnapshot;
import org.apache.nifi.registry.serialization.FlowContent;
import org.apache.nifi.registry.serialization.FlowContentSerializer;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import test.com.NiFiClusterOperatorMain;
import test.com.crd.NiFiClusters;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class NiFiClusterControllerTest {
    @Before
    public void before() {
    }

    @Test
    public void messWithRepo() throws IOException, NiFiRegistryException {
        final NiFiRegistryClientConfig clientConfig = new NiFiRegistryClientConfig.Builder()
                .baseUrl("http://localhost:18080")
                .build();

        assertNotNull(clientConfig);

        final NiFiRegistryClient client = new JerseyNiFiRegistryClient.Builder()
                .config(clientConfig)
                .build();

        final List<Bucket> retrievedBucket = client.getBucketClient().getAll();
        assertNotNull(retrievedBucket);
        assertEquals(1, retrievedBucket.size());
        Bucket bucket = retrievedBucket.get(0);
        String bucketId = bucket.getIdentifier();
        System.out.println(retrievedBucket.get(0));
        List<VersionedFlow> versionedFlows = client.getFlowClient().getByBucket(bucket.getIdentifier());
        assertNotNull(versionedFlows);
        assertEquals(1, versionedFlows.size());
        VersionedFlow versionedFlow = versionedFlows.get(0);
        String flowId = versionedFlow.getIdentifier();
        System.out.println(versionedFlow.getVersionCount());

        VersionedFlowSnapshot versionedFlowSnapshot = client.getFlowSnapshotClient().get(bucketId, flowId, 1);
        assertNotNull(versionedFlowSnapshot);
        FlowContentSerializer flowContentSerializer = new FlowContentSerializer();
        FlowContent flowContent = new FlowContent();
        flowContent.setFlowSnapshot(versionedFlowSnapshot);

        try(ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            flowContentSerializer.serializeFlowContent(flowContent, byteArrayOutputStream);
            System.out.println(byteArrayOutputStream.toString());
        }
    }
}