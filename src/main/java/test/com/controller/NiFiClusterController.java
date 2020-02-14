package test.com.controller;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSource;
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpec;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.fabric8.kubernetes.client.informers.cache.Lister;
import io.fabric8.zjsonpatch.internal.guava.Strings;
import test.com.crd.DoneableNiFiCluster;
import test.com.crd.NiFiCluster;
import test.com.crd.NiFiClusters;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NiFiClusterController {
    private BlockingQueue<String> workqueue;
    private SharedIndexInformer<NiFiCluster> niFiClusterInformer;
    private SharedIndexInformer<Pod> podInformer;
    private Lister<NiFiCluster> niFiClusterLister;
    private KubernetesClient kubernetesClient;
    private MixedOperation<NiFiCluster, NiFiClusters, DoneableNiFiCluster, Resource<NiFiCluster, DoneableNiFiCluster>> niFiClusterClient;
    private static Logger LOGGER = Logger.getLogger(NiFiClusterController.class.getName());

    public NiFiClusterController(KubernetesClient kubernetesClient, MixedOperation<NiFiCluster, NiFiClusters, DoneableNiFiCluster, Resource<NiFiCluster, DoneableNiFiCluster>> niFiClusterClient, SharedIndexInformer<Pod> podInformer, SharedIndexInformer<NiFiCluster> niFiClusterInformer, String namespace) {
        this.kubernetesClient = kubernetesClient;
        this.niFiClusterClient = niFiClusterClient;
        this.niFiClusterLister = new Lister<>(niFiClusterInformer.getIndexer(), namespace);
        this.niFiClusterInformer = niFiClusterInformer;
        this.podInformer = podInformer;
        this.workqueue = new ArrayBlockingQueue<>(1024);
    }

    public void create() {
        niFiClusterInformer.addEventHandler(new ResourceEventHandler<NiFiCluster>() {
            @Override
            public void onAdd(NiFiCluster niFiCluster) {
                LOGGER.log(Level.INFO, "NiFiCluster object added: " + niFiCluster);
                enqueueNiFiCluster(niFiCluster);
            }

            @Override
            public void onUpdate(NiFiCluster niFiCluster, NiFiCluster newNiFiCluster) {
                LOGGER.log(Level.INFO, "NiFiCluster object updated: " + niFiCluster);
                enqueueNiFiCluster(newNiFiCluster);
            }

            @Override
            public void onDelete(NiFiCluster niFiCluster, boolean b) {
                LOGGER.log(Level.INFO, "NiFiCluster object deleted: " + niFiCluster);
                deleteNiFiCluster(niFiCluster);
            }
        });
    }

    /**
     * Ensure a properly configured PersistentVolumeClaim exists as defined by the NiFiCluster
     *
     * @param niFiCluster The NiFiCluster that was just created or edited
     */
    private void reconcilePersistentVolumeClaim(NiFiCluster niFiCluster) {
        LOGGER.info("createPersistentVolumeClaim");

        String name = niFiCluster.getMetadata().getName().concat("-pvc");
        String namespace = niFiCluster.getMetadata().getNamespace();

        if(kubernetesClient.persistentVolumeClaims().inNamespace(namespace).withName(name).get() == null) {
            LOGGER.info("PVC " + name + " does not exist in namespace: " + namespace);
            PersistentVolumeClaim persistentVolumeClaim =  new PersistentVolumeClaimBuilder()
                    .withApiVersion("v1")
                    .withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
                    .withNewSpec()
                    .withNewStorageClassName("local-path")
                    .withAccessModes("ReadWriteOnce")
                    .withNewResources().addToRequests("storage", new Quantity("10Gi")).endResources()
                    .endSpec()
                    .build();

            try {
                LOGGER.info(kubernetesClient.persistentVolumeClaims().inNamespace(namespace).create(persistentVolumeClaim).toString());
                if (kubernetesClient.persistentVolumeClaims().inNamespace(namespace).withName(name).get() == null) {
                    throw new IllegalStateException("Unable to create PersistentVolumeClaim " + name + " in namespace " + namespace);
                }
            } catch (Exception e) {
                LOGGER.info("Exception " + e.getMessage());
            }
        } else {
            // UPDATE the PVC if necessary
        }
    }

    /**
     * Ensure a properly configured ConfigMap exists as defined by the NiFiCluster. This ConfigMap should contain
     * what will be the contents of the flow.xml.gz file for NiFi
     *
     * @param niFiCluster The NiFiCluster that was just created or edited
     */
    private void reconcileConfigMap(NiFiCluster niFiCluster) {
        String name = niFiCluster.getMetadata().getName() + "-flow-map";
        String namespace = niFiCluster.getMetadata().getNamespace();

        ConfigMap configMap = kubernetesClient.configMaps().inNamespace(namespace).withName(name).get();

        String flowXml = retrieveFlowXml();

        if(configMap == null) {
            configMap = new ConfigMapBuilder()
                    .withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
                    .addToData("flow.xml", flowXml)
                    .build();

            kubernetesClient.configMaps().createOrReplace(configMap);
        } else {
            // UPDATE the configmap
        }
    }

    /**
     * Retrieves a flow segment from a nifi registry based on information provided by the NiFiCluster object
     *
     * @return The flow segment
     */
    private String retrieveFlowXml() {
        // Hardcoded string for test purposes
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                "<flowController encoding-version=\"1.4\">\n" +
                "  <maxTimerDrivenThreadCount>10</maxTimerDrivenThreadCount>\n" +
                "  <maxEventDrivenThreadCount>1</maxEventDrivenThreadCount>\n" +
                "  <registries/>\n" +
                "  <parameterContexts/>\n" +
                "  <rootGroup>\n" +
                "    <id>30b87aef-0170-1000-81c5-96ae691312e9</id>\n" +
                "    <name>NiFi Flow</name>\n" +
                "    <position x=\"0.0\" y=\"0.0\"/>\n" +
                "    <comment/>\n" +
                "    <processor>\n" +
                "      <id>30b9602c-0170-1000-5d59-cedacbc7d9df</id>\n" +
                "      <name>GenerateFlowFile</name>\n" +
                "      <position x=\"523.0\" y=\"125.0\"/>\n" +
                "      <styles/>\n" +
                "      <comment/>\n" +
                "      <class>org.apache.nifi.processors.standard.GenerateFlowFile</class>\n" +
                "      <bundle>\n" +
                "        <group>org.apache.nifi</group>\n" +
                "        <artifact>nifi-standard-nar</artifact>\n" +
                "        <version>1.10.0-SNAPSHOT</version>\n" +
                "      </bundle>\n" +
                "      <maxConcurrentTasks>1</maxConcurrentTasks>\n" +
                "      <schedulingPeriod>0 sec</schedulingPeriod>\n" +
                "      <penalizationPeriod>30 sec</penalizationPeriod>\n" +
                "      <yieldPeriod>1 sec</yieldPeriod>\n" +
                "      <bulletinLevel>WARN</bulletinLevel>\n" +
                "      <lossTolerant>false</lossTolerant>\n" +
                "      <scheduledState>STOPPED</scheduledState>\n" +
                "      <schedulingStrategy>TIMER_DRIVEN</schedulingStrategy>\n" +
                "      <executionNode>ALL</executionNode>\n" +
                "      <runDurationNanos>0</runDurationNanos>\n" +
                "      <property>\n" +
                "        <name>File Size</name>\n" +
                "        <value>0B</value>\n" +
                "      </property>\n" +
                "      <property>\n" +
                "        <name>Batch Size</name>\n" +
                "        <value>1</value>\n" +
                "      </property>\n" +
                "      <property>\n" +
                "        <name>Data Format</name>\n" +
                "        <value>Text</value>\n" +
                "      </property>\n" +
                "      <property>\n" +
                "        <name>Unique FlowFiles</name>\n" +
                "        <value>false</value>\n" +
                "      </property>\n" +
                "      <property>\n" +
                "        <name>generate-ff-custom-text</name>\n" +
                "      </property>\n" +
                "      <property>\n" +
                "        <name>character-set</name>\n" +
                "        <value>UTF-8</value>\n" +
                "      </property>\n" +
                "    </processor>\n" +
                "  </rootGroup>\n" +
                "  <controllerServices/>\n" +
                "  <reportingTasks/>\n" +
                "</flowController>";
    }

    /**
     * Ensure a properly configured StatefulSet exists as defined by the NiFiCluster. This method programmatically creates
     * the StatefulSet similar to the one represented by statefulset.yaml in the resources directory.
     *
     * @param niFiCluster The NiFiCluster that was just created or edited
     */
    private void reconcileStatefulSet(NiFiCluster niFiCluster) {
        LOGGER.info("reconcileStatefulSet");
        String name = niFiCluster.getMetadata().getName();
        String namespace = niFiCluster.getMetadata().getNamespace();

        StatefulSet existingSet = kubernetesClient.apps().statefulSets().inNamespace(namespace).withName(name).get();

        // TODO Ensure this will be null if the statefulset object doesn't exist in k8s
        if(existingSet == null) {
            LOGGER.info("Statefulset is null, creating");
            ConfigMapVolumeSource configMapVolumeSource = new ConfigMapVolumeSourceBuilder()
                    .withName(name + "-flow-map")
                    .addNewItem().withKey("flow.xml").withNewPath("flow.xml").endItem()
                    .build();

            PodSpec podSpec = new PodSpecBuilder()
                    .addNewContainer()
                    .withNewName(name.concat("-container"))
                    .withImage(niFiCluster.getSpec().getImage()).withNewImagePullPolicy("Always")
                    .addNewVolumeMount().withName(name + "-pv").withMountPath("/share").endVolumeMount()
                    .addNewVolumeMount().withName(name + "-flow-volume").withMountPath("/flow-xml").endVolumeMount()
                    .addNewPort().withName("http-port").withContainerPort(8080).endPort()
                    .addNewPort().withName("https-port").withContainerPort(8443).endPort()
                    .addNewEnv().withName("NIFI_VERSION").withValue("1.10.0-SNAPSHOT").endEnv()
                    .addNewEnv().withName("SHARE_DIR").withValue("/share").endEnv()
                    .endContainer()
                    .addNewVolume().withName(name.concat("-pv")).withNewPersistentVolumeClaim().withClaimName(name.concat("-pvc")).endPersistentVolumeClaim().endVolume()
                    .addNewVolume().withName(name + "-flow-volume").withConfigMap(configMapVolumeSource).endVolume()
                    .build();

            PodTemplateSpec podTemplateSpec = new PodTemplateSpecBuilder()
                    .withNewMetadata().addToLabels("app", name).endMetadata()
                    .withSpec(podSpec)
                    .build();

            StatefulSetSpec statefulSetSpec = new StatefulSetSpecBuilder()
                    .withReplicas(niFiCluster.getSpec().getReplicas())
                    .withServiceName(name)
                    .withNewSelector().addToMatchLabels("app", name).endSelector()
                    .withTemplate(podTemplateSpec)
                    .build();

            StatefulSet statefulSet = new StatefulSetBuilder()
                    .withApiVersion("apps/v1")
                    .withKind("StatefulSet")
                    .withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
                    .withSpec(statefulSetSpec)
                    .build();

            kubernetesClient.apps().statefulSets().createOrReplace(statefulSet);
        } else {
            // UPDATE statefulset
        }
    }

    /**
     * This is the control loop. Continually poll a blocking queue in order to take action when a create or update
     * event has occurred. Deletes happen elsewhere.
     */
    public void run() throws InterruptedException {
        LOGGER.log(Level.INFO, "Starting NiFiCluster controller");
        while (!podInformer.hasSynced() || !niFiClusterInformer.hasSynced()) {
            Thread.sleep(50L);
        }

        while (true) {
            try {
                LOGGER.log(Level.INFO, "trying to fetch item from workqueue...");
                if (workqueue.isEmpty()) {
                    LOGGER.log(Level.INFO, "Work Queue is empty");
                }
                String key = workqueue.take();
                LOGGER.log(Level.INFO, "Got " + key);
                if (key.isEmpty() || (!key.contains("/"))) {
                    LOGGER.log(Level.WARNING, "invalid resource key: " + key);
                }

                // Get the NiFiCluster resource's name from key which is in format namespace/name
                String name = key.split("/")[1];
                NiFiCluster niFiCluster = niFiClusterLister.get(key.split("/")[1]);
                if (niFiCluster == null) {
                    LOGGER.log(Level.SEVERE, "NiFiCluster " + name + " in workqueue no longer exists");
                    return;
                }
                reconcile(niFiCluster);

            } catch (InterruptedException interruptedException) {
                LOGGER.log(Level.SEVERE, "controller interrupted..");
            }
        }
    }

    /**
     * Tries to achieve the desired state for niFiCluster.
     *
     * @param niFiCluster specified niFiCluster
     */
    private void reconcile(NiFiCluster niFiCluster) {
        LOGGER.info("addNiFiCluster");

        try {
            reconcilePersistentVolumeClaim(niFiCluster);
            reconcileConfigMap(niFiCluster);
            reconcileStatefulSet(niFiCluster);
        } catch (Exception e) {
            LOGGER.info("Exception: " + e.getMessage());
        }
    }

    private void enqueueNiFiCluster(NiFiCluster niFiCluster) {
        LOGGER.log(Level.INFO, "enqueueNiFiCluster(" + niFiCluster.getMetadata().getName() + ")");
        String key = Cache.metaNamespaceKeyFunc(niFiCluster);
        LOGGER.log(Level.INFO, "Going to enqueue key " + key);
        if(!Strings.isNullOrEmpty(key)) {
            LOGGER.log(Level.INFO, "Adding item to workqueue");
            workqueue.add(key);
        }
    }

    /**
     * Deletes all Kubernetes objects that were created for this NiFiCluster
     *
     * @param niFiCluster The CustomResource instance that was deleted
     */
    private void deleteNiFiCluster(NiFiCluster niFiCluster) {

    }
}
