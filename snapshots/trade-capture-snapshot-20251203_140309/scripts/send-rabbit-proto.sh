#!/usr/bin/env bash
set -e
PROTO_CLASS=com.yourorg.pms.proto.TradeEventProto

# Use a small Java runner jar or rabbitmqadmin; easiest: use rabbitmqadmin for JSON.
# For Protobuf payload, provide raw bytes with a Java helper. Here we provide a curl to management plugin
# but in dev better run a small Java snippet to publish protobuf bytes.

echo "Run the Java producer or use sample client to publish protobuf bytes"
