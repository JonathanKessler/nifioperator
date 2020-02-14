package test.com;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import test.com.controller.NiFiClusterController;
import test.com.crd.DoneableNiFiCluster;
import test.com.crd.NiFiCluster;
import test.com.crd.NiFiClusters;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main Class for Operator, you can run this sample using this command:
 *
 * mvn exec:java -Dexec.mainClass=io.fabric8.niFiCluster.operator.NiFiClusterOperatorMain
 */
public class NiFiClusterOperatorMain {
    private static Logger logger = Logger.getLogger(NiFiClusterOperatorMain.class.getName());

    public static void main(String[] args) {
        try (KubernetesClient client = new DefaultKubernetesClient()) {
            NiFiClusterController niFiClusterController = createNiFiClusterController(client);

            niFiClusterController.run();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static NiFiClusterController createNiFiClusterController(KubernetesClient client) {
        String namespace = client.getNamespace();
        if (namespace == null) {
            logger.log(Level.INFO, "No namespace found via config, assuming default.");
            namespace = "default";
        }

        logger.log(Level.INFO, "Using namespace : " + namespace);
        CustomResourceDefinition niFiClusterCustomResourceDefinition = new CustomResourceDefinitionBuilder()
                .withNewMetadata().withName("nificlusters.test.com").endMetadata()
                .withNewSpec()
                .withGroup("test.com")
                .withVersion("v1alpha1")
                .withNewNames().withKind("NiFiCluster").withPlural("nificlusters").endNames()
                .withScope("Namespaced")
                .endSpec()
                .build();
        CustomResourceDefinitionContext niFiClusterCustomResourceDefinitionContext = new CustomResourceDefinitionContext.Builder()
                .withVersion("v1alpha1")
                .withScope("Namespaced")
                .withGroup("test.com")
                .withPlural("nificlusters")
                .build();

        SharedInformerFactory informerFactory = client.informers();

        MixedOperation<NiFiCluster, NiFiClusters, DoneableNiFiCluster, Resource<NiFiCluster, DoneableNiFiCluster>> niFiClusterClient = client.customResources(niFiClusterCustomResourceDefinition, NiFiCluster.class, NiFiClusters.class, DoneableNiFiCluster.class);
        SharedIndexInformer<Pod> podSharedIndexInformer = informerFactory.sharedIndexInformerFor(Pod.class, PodList.class, 10 * 60 * 1000);
        SharedIndexInformer<NiFiCluster> niFiClusterSharedIndexInformer = informerFactory.sharedIndexInformerForCustomResource(niFiClusterCustomResourceDefinitionContext, NiFiCluster.class, NiFiClusters.class, 10 * 60 * 1000);
        NiFiClusterController niFiClusterController = new NiFiClusterController(client, niFiClusterClient, podSharedIndexInformer, niFiClusterSharedIndexInformer, namespace);

        niFiClusterController.create();
        informerFactory.startAllRegisteredInformers();

        return niFiClusterController;
    }
}
