# NiFi Operator

This operator continuously monitors operations performed on Custom Resources as defined by a
Custom Resource Definition (NiFiCluster), ensuring various Kubernetes objects exist and are
configured properly based on the values within that Custom Resource. It does this through the
fabric8 java client library. Ultimately this operator should be run within a Kubernetes deployment
to ensure that it is always up and running but can really be run anywhere provided it can reach
and access the Kubernetes cluster.

## Prerequisites

1) Deploy a private docker registry with custom image (see docker-registry README)

2) Deploy a privite nifi registry and control some flow(s) (see nifi-registry README)

3) Create the Custom Resource Definiton:

`kubectl create -f src/main/resources/crd.yaml`

4) Create the following directory on your k8s host (this is for demo purposes which use the host's local disk for some shared storage):

/opt/tarballs

## To Run:

The fabric8 java client can pull the information it needs to connect to Kubernetes from your kubeconfig
file. If it's not in the default spot of...whatever that is... set your ENV variable:

`KUBECONFIG=/path/to/kubeconfig.yaml`

At the root of this project:

`mvn clean install -DskipTests exec:java -Dexec.mainClass=test.com.NiFiClusterOperatorMain`

Create a custom resource object. An example:

`kubectl create -f src/test/resources/cr.yaml`
