FROM centos:latest

RUN yum install -y \
       java-1.8.0-openjdk \
       java-1.8.0-openjdk-devel

ENV JAVA_HOME /etc/alternatives/jre

ADD run.sh /run.sh
ADD nifi-1.10.0-SNAPSHOT-bin.tar.gz /nifi-1.10.0-SNAPSHOT-bin
ADD nifi-toolkit-1.11.2-bin.tar.gz /

RUN chmod +x run.sh

ENTRYPOINT ["/run.sh"]
