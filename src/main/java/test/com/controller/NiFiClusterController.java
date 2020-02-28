package test.com.controller;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSource;
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
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
import io.fabric8.kubernetes.api.model.apps.StatefulSetStatus;
import io.fabric8.kubernetes.api.model.apps.StatefulSetStatusBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.fabric8.kubernetes.client.informers.cache.Lister;
import io.fabric8.zjsonpatch.internal.guava.Strings;
import jdk.jfr.internal.LogLevel;
import test.com.crd.DoneableNiFiCluster;
import test.com.crd.NiFiCluster;
import test.com.crd.NiFiClusters;

import javax.xml.bind.DatatypeConverter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

        String name = niFiCluster.getMetadata().getName();
        String namespace = niFiCluster.getMetadata().getNamespace();

        if(kubernetesClient.persistentVolumeClaims().inNamespace(namespace).withName(name).get() == null) {
            LOGGER.info("PVC " + name + " does not exist in namespace: " + namespace);
            PersistentVolumeClaim persistentVolumeClaim = kubernetesClient.persistentVolumeClaims().load(NiFiClusterController.class.getResourceAsStream("/pvc.yaml")).get();
            persistentVolumeClaim.getMetadata().setName(name);
            persistentVolumeClaim.getMetadata().setNamespace(namespace);
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
        String name = niFiCluster.getMetadata().getName();
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
                "  <parameterContexts/>\n" +
                "  <rootGroup>\n" +
                "    <id>" + UUID.randomUUID().toString() + "</id>\n" +
                "    <name>NiFi Flow</name>\n" +
                "    <position x=\"0.0\" y=\"0.0\"/>\n" +
                "    <comment/>\n" +
                "  </rootGroup>\n" +
                "  <controllerServices/>\n" +
                "  <reportingTasks>\n" +
                "    <reportingTask>\n" +
                "      <id>87471357-0170-1000-c0e4-613e49f7bde6</id>\n" +
                "      <name>PrometheusReportingTask</name>\n" +
                "      <comment/>\n" +
                "      <class>org.apache.nifi.reporting.prometheus.PrometheusReportingTask</class>\n" +
                "      <bundle>\n" +
                "        <group>org.apache.nifi</group>\n" +
                "        <artifact>nifi-prometheus-nar</artifact>\n" +
                "        <version>1.10.0-SNAPSHOT</version>\n" +
                "      </bundle>\n" +
                "      <schedulingPeriod>60 sec</schedulingPeriod>\n" +
                "      <scheduledState>RUNNING</scheduledState>\n" +
                "      <schedulingStrategy>TIMER_DRIVEN</schedulingStrategy>\n" +
                "      <property>\n" +
                "        <name>prometheus-reporting-task-metrics-endpoint-port</name>\n" +
                "        <value>9092</value>\n" +
                "      </property>\n" +
                "      <property>\n" +
                "        <name>prometheus-reporting-task-instance-id</name>\n" +
                "        <value>${hostname(true)}</value>\n" +
                "      </property>\n" +
                "      <property>\n" +
                "        <name>prometheus-reporting-task-metrics-strategy</name>\n" +
                "        <value>All Components</value>\n" +
                "      </property>\n" +
                "      <property>\n" +
                "        <name>prometheus-reporting-task-metrics-send-jvm</name>\n" +
                "        <value>false</value>\n" +
                "      </property>\n" +
                "      <property>\n" +
                "        <name>prometheus-reporting-task-ssl-context</name>\n" +
                "      </property>\n" +
                "      <property>\n" +
                "        <name>prometheus-reporting-task-client-auth</name>\n" +
                "        <value>No Authentication</value>\n" +
                "      </property>\n" +
                "    </reportingTask>\n" +
                "  </reportingTasks>\n" +
                "</flowController>\n";
    }

    /**
     * Ensure a properly configured StatefulSet exists as defined by the NiFiCluster. This method programmatically creates
     * the StatefulSet similar to the one represented by statefulset.yaml in the resources directory.
     *
     * @param niFiCluster The NiFiCluster that was just created or edited
     */
    private void reconcileStatefulSet(NiFiCluster niFiCluster) throws Exception {
        LOGGER.info("reconcileStatefulSet");
        String name = niFiCluster.getMetadata().getName();
        String namespace = niFiCluster.getMetadata().getNamespace();

        StatefulSet existingSet = kubernetesClient.apps().statefulSets().inNamespace(namespace).withName(name).get();

        if(existingSet == null) {
            String yaml = Files.readString(Paths.get(getClass().getClassLoader().getResource("ss.yaml").toURI()));

            if(yaml.isEmpty()) {
                throw new Exception("Unable to retrieve yaml from resource file: ss.yaml");
            }

            LOGGER.info("Read the following YAML into memory: \n" + yaml);

            // Update statefulset with niFiCluster-specific info
            yaml = yaml.replaceAll("NAMESPACE", namespace).replaceAll("NAME", name);

            StatefulSet newStatefulSet = kubernetesClient.apps().statefulSets().load(new ByteArrayInputStream(yaml.getBytes())).get();
            List<EnvVar> envVarList = new ArrayList<>();
            envVarList.add(new EnvVarBuilder().withName("NIFI_VERSION").withValue("1.10.0-SNAPSHOT").build());
            envVarList.add(new EnvVarBuilder().withName("SHARE_DIR").withValue("/share").build());
            envVarList.add(new EnvVarBuilder().withName("REPOSITORY_URL").withValue(niFiCluster.getSpec().getRepositoryHost()).build());
            envVarList.add(new EnvVarBuilder().withName("FLOW_ID").withValue(niFiCluster.getSpec().getFlowId()).build());
            envVarList.add(new EnvVarBuilder().withName("BUCKET_ID").withValue(niFiCluster.getSpec().getBucketId()).build());
            envVarList.add(new EnvVarBuilder().withName("FLOW_VERSION").withValue(niFiCluster.getSpec().getFlowVersion()).build());
            newStatefulSet.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(envVarList);

            LOGGER.info("Statefulset does not exist, creating");
            kubernetesClient.apps().statefulSets().create(newStatefulSet);
        } else {
            LOGGER.info("Statefulset exists, updating");
            kubernetesClient.apps().statefulSets().inNamespace(namespace).withName(name).scale(niFiCluster.getSpec().getReplicas());
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
            LOGGER.log(Level.SEVERE, "Exception", e);
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
        String namespace = niFiCluster.getMetadata().getNamespace();
        String name = niFiCluster.getMetadata().getName();

        StatefulSet statefulSet = kubernetesClient.apps().statefulSets().inNamespace(namespace).withName(name).get();
        if(statefulSet != null) {
            kubernetesClient.apps().statefulSets().delete(statefulSet);
        }

        ConfigMap configMap = kubernetesClient.configMaps().inNamespace(namespace).withName(name).get();
        if(configMap != null) {
            kubernetesClient.configMaps().delete(configMap);
        }

        PersistentVolumeClaim persistentVolumeClaim = kubernetesClient.persistentVolumeClaims().inNamespace(namespace).withName(name).get();

        if(persistentVolumeClaim != null) {
            kubernetesClient.persistentVolumeClaims().delete(persistentVolumeClaim);
        }
    }
}
