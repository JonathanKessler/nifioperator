# NiFi Operator

This operator continuously monitors operations performed on Custom Resources as defined by a
Custom Resource Definition (NiFiCluster), ensuring various Kubernetes objects exist and are
configured properly based on the values within that Custom Resource. It does this through the
fabric8 java client library. Ultimately this operator should be run within a Kubernetes deployment
to ensure that it is always up and running but can really be run anywhere provided it can reach
and access the Kubernetes cluster.

## Prerequisites

Before running this operator, you must first create the Custom Resource Definiton:

`kubectl create -f src/main/resources/crd.yaml`

An example instance of that custom resource can be found the test resources directory:

`kubectl create -f src/test/resources/cr.yaml`

## To Run:

The fabric8 java client can pull the information it needs to connect to Kubernetes from your kubeconfig
file. If it's not in the default spot of...whatever that is... set your ENV variable:

`KUBECONFIG=/path/to/kubeconfig.yaml`

At the root of this project:

`mvn clean install -DskipTests exec:java -Dexec.mainClass=test.com.NiFiClusterOperatorMain`