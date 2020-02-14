package test.com.crd;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;


public class DoneableNiFiCluster extends CustomResourceDoneable<NiFiCluster> {
    public DoneableNiFiCluster(NiFiCluster resource, Function function) { super(resource, function); }
}
