package test.com.crd;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;

/*
 */
@JsonDeserialize(
        using = JsonDeserializer.None.class
)
public class NiFiClusterSpec implements KubernetesResource {
    private int replicas;
    private String image;
    private String flowId;
    private String flowVersion;
    private String repositoryHost;

    public int getReplicas() {
        return replicas;
    }

    public void setReplicas(int replicas) {
        this.replicas = replicas;
    }

    public String getFlowId() {
        return flowId;
    }

    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }

    public String getFlowVersion() {
        return flowVersion;
    }

    public void setFlowVersion(String flowVersion) {
        this.flowVersion = flowVersion;
    }

    public String getRepositoryHost() {
        return repositoryHost;
    }

    public void setRepositoryHost(String repositoryHost) {
        this.repositoryHost = repositoryHost;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    @Override
    public String toString() {
        return "NiFiClusterSpec{" +
                "replicas=" + replicas +
                ", image='" + image + '\'' +
                ", flowId='" + flowId + '\'' +
                ", flowVersion='" + flowVersion + '\'' +
                ", repositoryHost='" + repositoryHost + '\'' +
                '}';
    }
}
