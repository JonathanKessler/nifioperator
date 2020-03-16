# Image for Custom Resource

This is the Dockerfile and run script I've been using for the NiFi pods deployed by the operator.
It is assumed you already have a private docker repo running that you can push to.

`docker build -t <hostname>:5000/<imagename>:<version>`

`docker push <hostname>:5000/<imagename>:<version>`

Whatever you use for the above, you will need to update your yaml appropriately to match so it will
pull the proper image from your private repo. By default the yaml that comes with this
git project uses \<hostname>:5000/nifi:latest