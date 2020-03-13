#!/bin/bash

tar -xzf /tarballs/$NIFI_VERSION-bin.tar.gz
tar -xzf /tarballs/$TOOLKIT_VERSION-bin.tar.gz

cp /flow-xml/flow.xml /$NIFI_VERSION/conf/
NIFI_BIN=/$NIFI_VERSION/bin/nifi.sh
$NIFI_BIN start &

NIFI_URL=http://localhost:8000

TOOLKIT_BIN=/$TOOLKIT_VERISON/bin/cli.sh

COUNTER=0
echo "Waiting for NiFi to start for up to 10 minutes"
echo "$TOOLKIT_BIN nifi cluster-summary -u $NIFI_URL"
until $TOOLKIT_BIN nifi cluster-summary -u $NIFI_URL >& /dev/null || $COUNTER -eq 60 ]; do
  echo Iteration $COUNTER, waiting for NiFi to start: sleeping 10 seconds"
  COUNTER=$((COUNTER + 1))"
  sleep 10
done

echo "Attempting to register the NiFi registry at $REPOSITORY_URL"
echo "> $TOOLKIT_BIN nifi create-reg-client -u $NIFI_URL -rcn test -rcu $REPOSITORY_URL"
REG_ID=($TOOLKIT_BIN nifi create-reg-client -u $NIFI_URL -rcn test -rcu $REPOSITORY_URL) || { echo "Failed to register the NiFi Registry with error message: $REG_ID"; }

echo "Attempting to import a process group from Bucket $BUCKET_ID, Flow $FLOW_ID, Version $FLOW_VERSION"
echo "> $TOOLKIT_BIN nifi pg-import -b $BUCKET_ID -f $FLOW_ID -fv $FLOW_VERSION -u $NIFI_URL -rcid $REG_ID"
PG_ID=($TOOLKIT_BIN nifi pg-import -b $BUCKET_ID -f $FLOW_ID -fv $FLOW_VERSION -u $NIFI_URL -rcid $REG_ID) || { echo "Fauiled to import process group with error message: $PG_ID"; }

echo "Attempting to start newly imported process group $PG_ID"
echo "> $TOOLKIT_BIN nifi pg-start -pgid $PG_ID -u $NIFI_URL"
$TOOLKIT_BIN nifi pg-start -pgid $PG_ID -u $NIFI_URL || { echo "Failed to start newly imported process group."; }

# Use the existence of this file for a readiness probe
touch /tmp/ready

tail -F $NIFI_VERSION/logs/nifi-app.log