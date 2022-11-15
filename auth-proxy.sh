#!/usr/bin/env sh

~/.local/bin/oauth2-proxy \
      --provider=keycloak-oidc \
      --redirect-url=http://localhost:4180/oauth2/callback \
      --oidc-issuer-url=https://***REMOVED***/realms/sno \
      --http-address=0.0.0.0:4180 \
      --email-domain=* \
      --show-debug-on-error=true \
      --request-logging=true \
      --auth-logging=true \
      --standard-logging=true \
      --reverse-proxy=true \
      --client-id=dev-localhost \
      --client-secret=***REMOVED*** \
      --cookie-secret="***REMOVED***" \
      --cookie-csrf-per-request=true \
      --cookie-refresh="1m" \
      --cookie-expire="720m" \
      --skip-provider-button=true \
      --pass-host-header=true \
      --pass-user-headers=true \
      --pass-access-token=true \
      --upstream=http://localhost:6161
