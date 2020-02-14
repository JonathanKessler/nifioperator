package test.com.crd;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.CustomResource;

public class NiFiCluster extends CustomResource {
    public NiFiClusterSpec getSpec() {
        return spec;
    }

    public void setSpec(NiFiClusterSpec spec) {
        this.spec = spec;
    }

    public NiFiClusterStatus getStatus() {
        return status;
    }

    public void setStatus(NiFiClusterStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "NiFiCluster{"+
                "apiVersion='" + getApiVersion() + "'" +
                ", metadata=" + getMetadata() +
                ", spec=" + spec +
                ", status=" + status +
                "}";
    }

    private NiFiClusterSpec spec;
    private NiFiClusterStatus status;

    @Override
    public ObjectMeta getMetadata() {
        return super.getMetadata();
    }
}
