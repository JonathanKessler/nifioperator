# Private Docker Registry Within a Docker Container
These instructions are for running a private, secure docker registry within a docker container:

In order to run docker securely, you need server certs for it. If you do not already have some, you can create some via the following command. Ensure to use the FQDN as your hostname when prompted. The below 
certs can be made more secure if encrypted and pw protected etc. That's what google is for.

openssl req -newkey rsa:2048 -nodes -keyout registry_auth.key -x509 -days 365 -out registry_auth.crt

Also for demo purposes, create a directory on the host where you want to persist the registry's data:

`mkdir -p /opt/docker-container-data`

Use docker to deploy a registry on that host:

```
docker run -d -p 5000:5000 --restart=always --name=registry \
-v /path/to/cert/dir:/auth \
-v /opt/docker-container-data:/var/lib/registry \
-e "REGISTRY_HTTP_SECRET=<password>" \
-e "REGISTRY_HTTP_TLS_CERTIFICATE=/auth/registry_auth.crt" \
-e "REGISTRY_HTTP_TLS_KEY=/auth/registry_auth.key" \
-e "REGISTRY_HTTP_HOST=<hostname>" \
registry:2
```

In order for your Kubernetes cluster to be able to use this docker registry, it will need to be able to reach it as well as log in. The easiest way to do that is to generate credentials on a host somewhere by logging in, then copying the appropriate json file into a K8s secret:

Once docker is running in a container on your host, you may log into it and start pushing/pulling images:

docker login <host>

Enter * as the username and <password> as password

After successfully logging in, an entry should have appeared in your /home/\<username>/.docker/config.json file for your private repo.

Inform your Kubernetes cluster how to access your private repo by using that config.json file (if you have more credentials in there that you don't want to share, make a copy of the config.json file, remove the extra entries, and use that modified copy):

`kubectl create secret generic regcred --from-file=.dockerconfigjson=/path/to/config.json --type=kubernetes.io/dockerconfigjson`

Once that step is complete your K8s cluster should be able to pull images from your host simply by accessing the appropriate tag:

Image: \<hostname>:5000/imageName/version

See the README from customImageForNifi to deploy the operator's required image.
