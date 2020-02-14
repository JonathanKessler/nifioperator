package test.com.controller;

import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import test.com.NiFiClusterOperatorMain;
import test.com.crd.NiFiClusters;

public class NiFiClusterControllerTest {
    @Rule
    public KubernetesServer server = new KubernetesServer();

    private NiFiClusterController niFiClusterController;

    @Before
    public void before() {
        server.expect().withPath("/api/v1/namespaces/test/pods").andReturn(200, new PodListBuilder().build()).once();
        //server.expect().withPath("/apis/v1alpha1/namespaces/test/nificlusters").andReturn(200, new NiFiClusters());
      //  niFiClusterController = NiFiClusterOperatorMain.createNiFiClusterController(server.getClient());
    }

    @Test
    public void addNiFiCluster() throws InterruptedException {
    }
}