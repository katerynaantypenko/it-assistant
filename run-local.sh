#!/usr/bin/env bash
# Starts everything needed for local development. Press Ctrl+C to stop the three processes;
# Keycloak stays up in Docker and is stopped with "docker compose down".
set -euo pipefail

cd "$(dirname "$0")"

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

: "${OPENAI_API_KEY:?is not set, see .env.example}"

# The modules target Java 21, so Maven has to run on a JDK that can produce it. Checking here turns
# a confusing "release version 21 not supported" from javac into something actionable.
JAVA_MAJOR=$(mvn -v 2>/dev/null | sed -n 's/^Java version: \([0-9]*\).*/\1/p')
if [ -z "$JAVA_MAJOR" ] || [ "$JAVA_MAJOR" -lt 21 ]; then
  # On macOS a suitable JDK is often installed already, just not the default one.
  if NEWER_JDK=$(/usr/libexec/java_home -v 21+ 2>/dev/null); then
    export JAVA_HOME="$NEWER_JDK"
    echo "Maven ran on Java ${JAVA_MAJOR:-unknown}, switching to the JDK in $JAVA_HOME"
  else
    echo "This project needs JDK 21 or newer, but Maven runs on Java ${JAVA_MAJOR:-unknown}." >&2
    echo "Install a JDK 21+ and point JAVA_HOME at it, then run this script again." >&2
    exit 1
  fi
fi

ISSUER_URI="${OIDC_ISSUER_URI:-http://localhost:8180/realms/it-assistant}"

# Only the bundled provider is ours to start: an external issuer is already running somewhere.
if [[ "$ISSUER_URI" == http://localhost:8180/* ]]; then
  echo "Starting Keycloak on http://localhost:8180"
  docker compose up -d keycloak

  # The chat backend reads the discovery document at startup, so wait for the realm to be imported.
  printf "Waiting for the realm to be imported"
  until curl -sfo /dev/null "$ISSUER_URI/.well-known/openid-configuration"; do
    printf "."
    sleep 2
  done
  echo " ready"
fi

trap 'kill 0' EXIT

echo "Starting mcp-kb-server on http://localhost:8081/mcp"
mvn -pl mcp-kb-server spring-boot:run &

echo "Starting chat-backend on http://localhost:8080"
mvn -pl chat-backend spring-boot:run &

echo "Starting chat-web on http://localhost:5173"
(cd chat-web && npm run dev) &

wait
