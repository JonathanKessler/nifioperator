#!/bin/bash

# This script expects access to a mounted directory named "/tarballs" that contains both an unpacked nifi as well as nifi-toolkit.
# It will create a directory structure using predefined variables BUCKET_ID, FLOW_ID, FLOW_VERSION and HOSTNAME so that between pod
# restarts it can easily find the repositories it was previously working with. This of course assumeses that that directory is mounted
# in such a way as to be persisted between pod restarts.

# The script uses the nifi-toolkit to ensure nifi is up and running. If it is the first time this pod was started with the given
# bucket id, flow id, version # and hostname then it will then use the toolkit to attempt to pull the appropriate flow from a nifi registry.
# If that is succesful or not needed, it will then indicate readiness to the pod by touching the file /tmp/ready.

# If NiFi fails to start in time or we are unable to pull the appropriate flow, this script will exit with an error code which should cause
# the pod to terminate.

NIFI_URL=http://localhost:8080
NIFI_HOME=/share/$BUCKET_ID/$FLOW_ID/$FLOW_VERSION/$HOSTNAME/nifi
echo "Checking for the existence of the directory $NIFI_HOME"

# If this is the first time this bucket/flow/version/hostname has been deployed, lay out the appropriate directory structure
if [ ! -d "$NIFI_HOME" ]; then
  NIFI_HOME_EXISTED=false
  echo "$NIFI_HOME does not exist, creating".
  mkdir -p $NIFI_HOME

  cd $NIFI_HOME
  NIFI_BASE_DIR=/tarballs/$NIFI_VERSION
  cp -rf $NIFI_BASE_DIR/bin .
  cp -rf $NIFI_BASE_DIR/conf .
  ln -s $NIFI_BASE_DIR/lib lib
  ln -s $NIFI_BASE_DIR/work work
  cp /flow-xml/flow.xml $NIFI_HOME/conf/
else
  echo "NIFI_HOME exists, attempting to start nifi"
fi

NIFI_BIN=/$NIFI_HOME/bin/nifi.sh
$NIFI_BIN start &

TOOLKIT_BIN=/tarballs/$TOOLKIT_VERSION/bin/cli.sh

COUNTER=0
MAX_COUNTER=60
WAIT_TIME=10
echo "Waiting for NiFi to start for up to $MAX_COUNTER iterations of $WAIT_TIME seconds"
echo "$TOOLKIT_BIN nifi cluster-summary -u $NIFI_URL"
until $TOOLKIT_BIN nifi cluster-summary -u $NIFI_URL >& /dev/null || [ $COUNTER -eq $MAX_COUNTER ]; do
  echo "Iteration $COUNTER, waiting for NiFi to start: sleeping $WAIT_TIME seconds"
  COUNTER=$((COUNTER + 1))
  sleep $WAIT_TIME
done

# Ensure NiFi started before we hit the iteration limit
if [ $COUNTER -eq $MAX_COUNTER ]; then
  echo "Reached $MAX_COUNTER iterations without NiFi starting successfully. Exiting program."
  exit 1
fi

# If this is the first time this bucket/flow/version/hostname has been deployed, pull the appropriate flow from the nifi registry.
# TODO: Exit on failure at any point. For demo/dev purposes, just spit out errors and keep going
if [ ! -z ${NIFI_HOME_EXISTED} ]; then
  # First register the NiFi registry with this running NiFi
  echo "This is a new NiFi, attempting to register the NiFi registry at $REPOSITORY_URL"
  echo "> $TOOLKIT_BIN nifi create-reg-client -u $NIFI_URL -rcn test -rcu $REPOSITORY_URL"
  REG_ID=$($TOOLKIT_BIN nifi create-reg-client -u $NIFI_URL -rcn test -rcu $REPOSITORY_URL) || { echo "Failed to register the NiFi Registry with error message: $REG_ID"; }
  echo "Result of command: $REG_ID"

  # Import the appropriate flow
  echo "Attempting to import a process group from Bucket $BUCKET_ID, Flow $FLOW_ID, Version $FLOW_VERSION"
  echo "> $TOOLKIT_BIN nifi pg-import -b $BUCKET_ID -f $FLOW_ID -fv $FLOW_VERSION -u $NIFI_URL -rcid $REG_ID"
  PG_ID=$($TOOLKIT_BIN nifi pg-import -b $BUCKET_ID -f $FLOW_ID -fv $FLOW_VERSION -u $NIFI_URL -rcid $REG_ID) || { echo "Failed to import process group with error message: $PG_ID"; }
  echo "Result of command: $PG_ID"

  # Start the appropriate flow
  echo "Attempting to start newly imported process group $PG_ID"
  echo "> $TOOLKIT_BIN nifi pg-start -pgid $PG_ID -u $NIFI_URL"
  $TOOLKIT_BIN nifi pg-start -pgid $PG_ID -u $NIFI_URL || { echo "Failed to start newly imported process group."; }
else
  echo "This pod has been restarted and should already have the appropriate flow in place."
fi

echo "Pod is ready."
touch /tmp/ready
tail -F $NIFI_HOME/logs/nifi-app.log
