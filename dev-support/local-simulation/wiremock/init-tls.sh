#!/bin/sh
set -eu

if [ ! -f /run/tls/wiremock.p12 ]; then
  keytool -genkeypair -alias local-sim -keyalg RSA -keysize 2048 -validity 30 \
    -dname "CN=localhost" -ext "SAN=dns:localhost,ip:127.0.0.1" \
    -storetype PKCS12 -keystore /run/tls/wiremock.p12 \
    -storepass local-sim-password -keypass local-sim-password
fi
echo 'WireMock TLS fixture ready'
