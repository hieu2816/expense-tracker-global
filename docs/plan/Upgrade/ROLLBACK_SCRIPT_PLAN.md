#!/bin/bash
# script/rollback.sh
# Usage: ./rollback.sh [git_sha]
# If no git_sha is provided, it rolls back to the last successful deployment.

set -e

# Define paths
ENV_FILE=".env"
LAST_SUCCESS_FILE=".last_successful_deploy"

# Determine target SHA
if [[ -n "$1" ]]; then
    TARGET_SHA="$1"
    echo "🔄 Manual rollback requested to Git SHA: $TARGET_SHA"
else
    if [[ ! -f "$LAST_SUCCESS_FILE" ]]; then
        echo "❌ Error: No specific SHA provided and $LAST_SUCCESS_FILE not found."
        exit 1
    fi
    TARGET_SHA=$(cat "$LAST_SUCCESS_FILE")
    echo "🔄 Auto rollback triggered to last successful deploy: $TARGET_SHA"
fi

# We need the repository name to construct the GHCR URL
# Extract it from the current FRONTEND_IMAGE or BACKEND_IMAGE in .env, or use a default
if grep -q "BACKEND_IMAGE" "$ENV_FILE"; then
    REPO_PATH=$(grep "BACKEND_IMAGE" "$ENV_FILE" | cut -d'=' -f2 | awk -F'/' '{print $2"/"$3}' | cut -d':' -f1)
else
    echo "❌ Error: Could not determine repository path from $ENV_FILE"
    exit 1
fi

echo "📦 Using GHCR Repository Path: $REPO_PATH"

# Update the .env file with the target SHA
sed -i "s|BACKEND_IMAGE=.*|BACKEND_IMAGE=ghcr.io/${REPO_PATH}:$TARGET_SHA|g" "$ENV_FILE"
sed -i "s|FRONTEND_IMAGE=.*|FRONTEND_IMAGE=ghcr.io/${REPO_PATH}:$TARGET_SHA|g" "$ENV_FILE"

echo "✅ Updated .env with target images."

# Pull the old images and restart
echo "⬇️ Pulling images for $TARGET_SHA..."
docker compose pull

echo "🚀 Restarting containers..."
docker compose up -d

# Verify health
echo "⏳ Waiting 15 seconds for containers to stabilize..."
sleep 15

bash script/smoke-test.sh || {
    echo "❌ Rollback FAILED! The old version ($TARGET_SHA) also failed smoke tests."
    exit 1
}

echo "✅ Rollback to $TARGET_SHA completed successfully!"
