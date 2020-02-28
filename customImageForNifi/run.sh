#!/bin/sh

HOSTNAME=(`hostname`)
FLOW_XML_PATH=/flow-xml/flow.xml

if [ -z "$NIFI_VERSION" ]; then
  echo "Missing ENV variable \$NIFI_VERSION"
  exit 1
fi

if [ -z "$SHARE_DIR" ]; then
  echo "Missing ENV variable \$SHARE_DIR"
  exit 1
fi

NIFI_BASE_DIR=/$SHARE_DIR/$HOSTNAME/nifi-$NIFI_VERSION

if [ -d /$SHARE_DIR/$HOSTNAME ]; then
  # Kind of hacky but if we've previously unpacked /share/hostname then no need to do it again. We can probably download and
  # unpack this file instead of it bloating the image
  rm -rf nifi-$NIFI_VERSION-bin
else
  mv nifi-$NIFI_VERSION-bin /$SHARE_DIR/$HOSTNAME/
fi

if [ -f $FLOW_XML_PATH ]; then
  echo "Flow xml was provided, zipping to conf directory"
  gzip $FLOW_XML_PATH -c > $NIFI_BASE_DIR/conf/flow.xml.gz
fi

$NIFI_BASE_DIR/bin/nifi.sh start &

NIFI_URL=http://localhost:8080

# TODO: Make this version configurable and add a line to download it or add it to the image
TOOLKIT_BIN=/nifi-toolkit-1.11.2/bin/cli.sh

COUNTER=0
echo "Waiting for NiFi to start for up to 60 seconds"
sleep 2
until $TOOLKIT_BIN nifi cluster-summary -u $NIFI_URL >& /dev/null  || [ $COUNTER -eq 30 ]; do
   echo "Iteration $COUNTER, Waiting for NiFi to start: sleeping 2 seconds"
   COUNTER=$((COUNTER + 1))
   sleep 2
done

echo "Attempting to register the NiFi registry at $REPOSITORY_URL"
REG_ID=$($TOOLKIT_BIN nifi create-reg-client -u $NIFI_URL -rcn test -rcu $REPOSITORY_URL) || { echo "Failed to register the NiFi Registry with error message: $REG_ID"; }
echo "Attempting to import a process group from Bucket $BUCKET_ID, Flow $FLOW_ID, Version $FLOW_VERSION"
PG_ID=$($TOOLKIT_BIN nifi pg-import -b $BUCKET_ID -f $FLOW_ID -fv $FLOW_VERSION -u $NIFI_URL -rcid $REG_ID) || { echo "Failed to import process group with error message: $PG_ID"; }
echo "Attempting to start newly imported process group $PG_ID"
$TOOLKIT_BIN nifi pg-start -pgid $PG_ID -u $NIFI_URL || { echo "Failed to start newly imported process group."; }

# Use the existence of this file for a readiness probe
touch /tmp/ready

tail -F $NIFI_BASE_DIR/logs/nifi-app.log
