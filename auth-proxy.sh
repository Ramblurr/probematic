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
      --cookie-expire="30m" \
      --skip-provider-button=true \
      --pass-host-header=true \
      --pass-user-headers=true \
      --pass-access-token=true \
      --upstream=http://localhost:6161

# -----BEGIN PUBLIC KEY-----
# MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAt8vGXMaB+9vgj4tAfCKrGlX+NW7A2Dvj0G0rKocF2yofza6QnaGhNRhglXaLlwXUb4ANHCXOds0bzhmJRmQLAJ0o8ReUiyblExZ8dXN7duZzONKTmRBNAZOOkYMm8yU17/NYqaomNJ7aJh0GamXg4NgXRhS3NNiUrrT4+j46Vi2ldwGqB6YDc2DM3QH/tOpFx/xT7t2RKWSUXGdamRKcghuUFwpOfESWnIDBXqiwrbzT8RyPPqHEOUNugIzsrPoPUQdxzPMvMcihBq1hKJozB+xnIt9U5hMxqAygldfIdZBcIX/ffox+ZK2SJbv/pE6O12t0QtlaKX1V5NWgFPa8RQIDAQAB
# -----END PUBLIC KEY-----
