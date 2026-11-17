#!/bin/bash
# Instala kind con versión fija y comprobación de la suma de verificación.
# Las demás herramientas (JDK, Maven, Node, Docker, kubectl) llegan como features del devcontainer.
set -euo pipefail
VERSION=v0.33.0
ARQ=$(dpkg --print-architecture)
URL="https://kind.sigs.k8s.io/dl/${VERSION}/kind-linux-${ARQ}"
TMP=$(mktemp -d)
curl -fsSL "$URL" -o "$TMP/kind"
curl -fsSL "$URL.sha256sum" -o "$TMP/kind.sha256sum"
(cd "$TMP" && echo "$(cut -d' ' -f1 kind.sha256sum)  kind" | sha256sum -c -)
sudo install -m 0755 "$TMP/kind" /usr/local/bin/kind
rm -rf "$TMP"
kind version
