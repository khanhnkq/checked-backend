#!/bin/bash
set -e

CONFIG_FILE="/etc/garage.toml"
KEY_ID="${GARAGE_ACCESS_KEY_ID:-GK000000000000000000000001}"
SECRET_KEY="${GARAGE_SECRET_ACCESS_KEY:-0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef}"
BUCKET_NAME="${GARAGE_BUCKET:-locket-photos}"

echo "Waiting for Garage daemon to be ready..."
RETRIES=30
until garage -c "$CONFIG_FILE" status 2>/dev/null | grep -q "HEALTHY NODES" || [ $RETRIES -eq 0 ]; do
    echo "Garage is not ready yet, retrying in 1s... ($RETRIES left)"
    sleep 1
    RETRIES=$((RETRIES - 1))
done

if [ $RETRIES -eq 0 ]; then
    echo "Timed out waiting for Garage daemon!"
    exit 1
fi

echo "Garage daemon is online. Checking cluster layout..."
NODE_ID=$(garage -c "$CONFIG_FILE" status | grep -oE '[a-f0-9]{16,64}' | head -n 1)

if [ -n "$NODE_ID" ]; then
    echo "Found Garage Node ID: $NODE_ID"
    # Assign layout if needed
    garage -c "$CONFIG_FILE" layout assign -z dc1 -c 10G "$NODE_ID" 2>/dev/null || true
    garage -c "$CONFIG_FILE" layout apply --version 1 2>/dev/null || true

    # Import static key if not exists
    garage -c "$CONFIG_FILE" key import --yes -n locket-app-key "$KEY_ID" "$SECRET_KEY" 2>/dev/null || true

    # Create bucket and grant permissions
    garage -c "$CONFIG_FILE" bucket create "$BUCKET_NAME" 2>/dev/null || true
    garage -c "$CONFIG_FILE" bucket allow "$BUCKET_NAME" --read --write --key "$KEY_ID" 2>/dev/null || true
    garage -c "$CONFIG_FILE" bucket website --allow "$BUCKET_NAME" 2>/dev/null || true

    echo "Garage S3 cluster initialization completed successfully!"
else
    echo "Could not find Node ID from garage status!"
    exit 1
fi
