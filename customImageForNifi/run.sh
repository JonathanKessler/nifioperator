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
  echo EXISTS
  rm -rf nifi-$NIFI_VERSION-bin
else
  mv nifi-$NIFI_VERSION-bin /$SHARE_DIR/$HOSTNAME/
fi

if [ -f $FLOW_XML_PATH ]; then
  echo "Flow xml was provided"
  gzip $FLOW_XML_PATH -c > $NIFI_BASE_DIR/conf/flow.xml.gz
fi

$NIFI_BASE_DIR/bin/nifi.sh start &
tail -F $NIFI_BASE_DIR/logs/nifi-app.log
