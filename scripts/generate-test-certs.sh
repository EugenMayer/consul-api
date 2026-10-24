#!/bin/sh
# Regenerates the TLS material for the HTTPS integration test (ConsulClientTest) in src/test/resources/ssl:
#   ca.cert         test CA certificate, Consul's ca_file
#   key.crt/key.key server certificate and key for localhost, Consul's cert_file/key_file
#   trustStore.jks  JKS trust store with the test CA, used by the test client
# Requires openssl and keytool (JDK). Test-only material, never use it anywhere else.
set -eu

DAYS=3650
STORE_PASSWORD=change_me
OUT="$(cd "$(dirname "$0")/.." && pwd)/src/test/resources/ssl"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# CA
openssl req -x509 -newkey rsa:2048 -nodes -days "$DAYS" -sha256 \
  -keyout "$WORK/ca.key" -out "$WORK/ca.cert" \
  -subj "/O=consul-api test CA/CN=consul-api test CA" \
  -addext "basicConstraints=critical,CA:TRUE" -addext "keyUsage=critical,keyCertSign,cRLSign"

# server certificate for the Consul HTTPS listener, reached via localhost
openssl req -newkey rsa:2048 -nodes -sha256 \
  -keyout "$WORK/key.key" -out "$WORK/key.csr" -subj "/O=consul-api test/CN=localhost"
cat > "$WORK/server.ext" <<EOF
basicConstraints=CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=DNS:localhost,IP:127.0.0.1,IP:::1
EOF
openssl x509 -req -in "$WORK/key.csr" -CA "$WORK/ca.cert" -CAkey "$WORK/ca.key" -CAcreateserial \
  -days "$DAYS" -sha256 -extfile "$WORK/server.ext" -out "$WORK/key.crt"

# trust store for the Java client
keytool -importcert -noprompt -alias custom-ca -file "$WORK/ca.cert" \
  -keystore "$WORK/trustStore.jks" -storetype JKS -storepass "$STORE_PASSWORD"

mkdir -p "$OUT"
cp "$WORK/ca.cert" "$WORK/key.crt" "$WORK/key.key" "$WORK/trustStore.jks" "$OUT/"
# Consul in the container runs as a different user and must be able to read the key
chmod 644 "$OUT/key.key"
echo "Test certificates written to $OUT (valid for $DAYS days)"
