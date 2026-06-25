# Android OAuth2 Token Exchange — Code Analysis
## Pre-DPoP baseline for W-22695293

This document analyzes the Android OAuth2 token exchange implementation as it exists today
(pre-DPoP). It is written to guide the DPoP implementation (W-22695293) by identifying
exact integration points and their current behavior.

---

## 1. Primary Files

### 1a. `libs/SalesforceSDK/src/com/salesforce/androidsdk/auth/OAuth2.java`
The central OAuth2 helper class. All token endpoint calls are static methods.

### 1b. `libs/SalesforceSDK/src/com/salesforce/androidsdk/rest/RestClient.java`
The REST client. Contains `OAuthRefreshInterceptor`, which intercepts 401 responses and
triggers token refresh before retrying.

---

## 2. Token Exchange Flow

### 2a. Authorization Code Exchange

**Method:** `OAuth2.exchangeCode()`

```java
public static TokenEndpointResponse exchangeCode(
    HttpAccess httpAccessor, URI loginServer,
    String clientId, String code, String codeVerifier, String callbackUrl)
    throws OAuthFailedException, IOException
```

**What it does:**
1. Builds a `FormBody` with: `grant_type`, `client_id`, `format=json`, `code`, `code_verifier`, `redirect_uri`
2. Delegates to `makeTokenEndpointRequest()`

**Integration path from login UI:**
`LoginViewModel.doCodeExchange()` → `OAuth2.exchangeCode()` → `makeTokenEndpointRequest()`

**DPoP hook:** `makeTokenEndpointRequest()` is the single entry point — this is where
the `DPoP` header must be added for both code exchange and refresh.

---

### 2b. Token Refresh

**Method:** `OAuth2.refreshAuthToken()`

```java
public static TokenEndpointResponse refreshAuthToken(
    HttpAccess httpAccessor, URI loginServer,
    String clientId, String refreshToken, Map<String,String> addlParams)
    throws OAuthFailedException, IOException
```

**What it does:**
1. Builds a `FormBody` with: `grant_type` (refresh_token or hybrid_refresh), `client_id`,
   `refresh_token`, `format=json`, optional `addlParams`
2. Delegates to `makeTokenEndpointRequest()`

**Integration path from expired session:**
`OAuthRefreshInterceptor.refreshAccessToken()` → `authTokenProvider.getNewAuthToken()` →
... → `OAuth2.refreshAuthToken()` → `makeTokenEndpointRequest()`

**DPoP hook:** Same — `makeTokenEndpointRequest()`.

---

### 2c. `makeTokenEndpointRequest()` — The Central Integration Point

```java
@VisibleForTesting @WorkerThread
public static TokenEndpointResponse makeTokenEndpointRequest(
    HttpAccess httpAccessor, URI loginServer,
    FormBody.Builder formBodyBuilder,
    SalesforceSDKManager salesforceSdkManager)
    throws OAuthFailedException, IOException
```

**Current flow:**
```
1. Build URL: loginServer + "/services/oauth2/token" + "?device_id=..."
2. (Optional) Append attestation to query string if available
3. Build OkHttp Request: POST, RequestBody from formBodyBuilder
4. Execute via httpAccessor.getOkHttpClient().newCall(request).execute()
5. If successful → return new TokenEndpointResponse(response)
6. If failed → throw OAuthFailedException(new TokenErrorResponse(response), status)
```

**DPoP changes needed here (Phase 2):**
- After step 2/3, build a DPoP proof JWT and add it as the `DPoP` header
- After step 5, parse `token_type` from response and persist it
- Wrap step 4 in a nonce-retry loop (Phase 4)

**Method signature change needed:**
The current method doesn't accept a `credentialsIdentifier` (scope key for key store lookup).
This needs to be threaded in from callers (`exchangeCode` and `refreshAuthToken`).

---

## 3. Authorization Header: Current Baseline

### `OAuth2.addAuthorizationHeader()`

```java
public static Request.Builder addAuthorizationHeader(Request.Builder builder, String authToken) {
    return builder.header(AUTHORIZATION, BEARER + authToken);
    // AUTHORIZATION = "Authorization"
    // BEARER = "Bearer "
}
```

**Used in:**
- `OAuth2.callIdentityService()` — identity endpoint call
- `RestClient.OAuthRefreshInterceptor.setAuthHeader()` — all API calls via interceptor

**DPoP changes needed (Phase 2):**
- Accept a `tokenType` parameter
- When `tokenType == "DPoP"`: use `"DPoP "` prefix instead of `"Bearer "`
- Phase 3 will also need to add the `DPoP` proof header at this point

---

## 4. `OAuthRefreshInterceptor` — API Call Authentication

**Location:** `RestClient.java`, inner static class

**Lifecycle:** One instance per user account, stored in `OAUTH_REFRESH_INTERCEPTORS` cache.

**Fields:**
```java
private final AuthTokenProvider authTokenProvider;
private String authToken;
private ClientInfo clientInfo;
```

### 4a. `intercept()` — The OkHttp Interceptor

```
1. buildAuthenticatedRequest(request)    ← adds Authorization: Bearer <token>
2. chain.proceed(request)                ← send the request
3. if shouldRefresh(response):           ← 401 or 403-with-Bad_OAuth_Token
   a. refreshAccessToken()               ← calls authTokenProvider.getNewAuthToken()
   b. if new token is not null:
      - buildAuthenticatedRequest(request)   ← re-stamp with new token
      - adjust host if instanceUrl changed   ← instance migration
      - chain.proceed(request)               ← retry
4. return response
```

**DPoP changes needed (Phase 3):**
- `buildAuthenticatedRequest()` must also attach the `DPoP` proof header when `tokenType == "DPoP"`
- `shouldRefresh()` must also detect `use_dpop_nonce` challenges (Phase 4)

### 4b. `buildAuthenticatedRequest()`

```java
private Request buildAuthenticatedRequest(Request request) {
    Request.Builder builder = request.newBuilder();
    setAuthHeader(builder);
    return builder.build();
}

private void setAuthHeader(Request.Builder builder) {
    if (authToken != null) {
        OAuth2.addAuthorizationHeader(builder, authToken);
    }
}
```

**Current behavior:** Always `Authorization: Bearer <token>`.

**DPoP changes (Phase 3):** Must check tokenType and call DPoP decorator.

---

## 5. `TokenEndpointResponse` — Response Parsing

**Location:** `OAuth2.java`, static inner class

**Current fields (relevant to DPoP):**
```java
public String authToken;       // "access_token"
public String refreshToken;    // "refresh_token"
public String instanceUrl;     // "instance_url"
public String tokenFormat;     // "token_format"
// ... many other fields
```

**`TokenEndpointResponse(Response response)` constructor:**
Parses JSON from the HTTP response body. Fields are populated via:
```java
authToken = parsedResponse.getString(ACCESS_TOKEN);
instanceUrl = parsedResponse.getString(INSTANCE_URL);
tokenFormat = parsedResponse.optString(TOKEN_FORMAT);
// ... etc.
```

**DPoP change needed (Phase 2):**
Add `tokenType` field and populate it:
```java
public String tokenType;       // "token_type" — "DPoP" or "Bearer"
// In constructor:
tokenType = parsedResponse.optString(TOKEN_TYPE);  // add TOKEN_TYPE = "token_type" constant
```

**Where this propagates:**
`TokenEndpointResponse` is consumed by `LoginViewModel` and account management code, which
populate `UserAccount`. The `tokenType` must be stored in `UserAccount` (or the equivalent
credential storage) so it's available to `OAuthRefreshInterceptor` at API call time.

---

## 6. `TokenErrorResponse` — Error Parsing

**Current fields:**
```java
public String error;
public String errorDescription;
```

**DPoP relevance:** `invalid_dpop_proof` errors at code exchange will surface here.
The SDK currently surfaces these generically — no special handling. For Phase 2, the
error propagation path is already in place; DPoP-specific errors are readable via
`getTokenErrorResponse().error` on the `OAuthFailedException`.

---

## 7. `LoginViewModel.doCodeExchange()` — Error Handling

**Location:** `libs/SalesforceSDK/src/com/salesforce/androidsdk/auth/LoginViewModel.kt`

The code exchange flow:
```kotlin
try {
    val response = OAuth2.exchangeCode(...)
    // on success: store tokens, proceed to identity service
} catch (e: OAuth2.OAuthFailedException) {
    onAuthFlowError(...)
    // broadcasts AUTHENTICATION_FAILED_INTENT
    // shows a Toast
    // reloads the login page
}
```

**DPoP error visibility:** When `invalid_dpop_proof` is returned, it is surfaced as a
generic OAuth error. The error and error_description from the token response are preserved
in `OAuthFailedException.response` (`TokenErrorResponse`), so apps can distinguish DPoP
failures if they catch the broadcast.

---

## 8. `SalesforceSDKManager` — Configuration Entry Point

**Location:** `libs/SalesforceSDK/src/com/salesforce/androidsdk/app/SalesforceSDKManager.java`

Relevant to DPoP:
- **Opt-in flag needed:** Add `private boolean useDPoP = false;` with getter/setter
- **Device ID:** `getDeviceId()` — already used in token endpoint URL; provides a stable
  identifier pattern (but DPoP scope key should be the user credentials ID, not device ID)
- **App Attestation pattern:** The attestation integration (conditionally appends a query
  parameter) provides a good template for how to conditionally add the DPoP header in
  `makeTokenEndpointRequest()`

---

## 9. Android Keystore (DPoP Key Storage Context)

The Android Keystore API provides hardware-backed key storage:

```kotlin
// Generate EC/P-256 key pair
val keyPairGenerator = KeyPairGenerator.getInstance(
    KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
keyPairGenerator.initialize(
    KeyGenParameterSpec.Builder(
        alias,  // "dpop_" + credentialsIdentifier
        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
    )
    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))  // P-256
    .setDigests(KeyProperties.DIGEST_SHA256)
    .build()
)
val keyPair = keyPairGenerator.generateKeyPair()
```

**Key retrieval:**
```kotlin
val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
val privateKey = keyStore.getKey(alias, null) as PrivateKey
val publicKey = keyStore.getCertificate(alias).publicKey as ECPublicKey
```

**No simulator special-casing needed** (unlike iOS). Android Keystore works on emulators
(software-backed) and real devices (hardware-backed) with the same API.

**Key accessibility:** Keys are accessible to the app after device boot, without user
authentication required — appropriate for background token refreshes.

---

## 10. Existing Tests

### 10a. `OAuth2MockTests.kt` (mock-based unit tests)

**Location:** `libs/test/SalesforceSDKTest/src/com/salesforce/androidsdk/auth/OAuth2MockTests.kt`

**Framework:** Kotlin + MockK + AndroidJUnit4

**Pattern:** Uses `mockk<>` to mock `SalesforceSDKManager`, `AppAttestationClient`,
`HttpAccess`, and `OkHttpClient`. Captures the outbound `Request` via a `slot<Request>()`
and asserts on its URL/body.

**Existing tests:**
- `oauth2_getAuthorizationUrl_includesAttestationParameterWhenNotNull`
- `oauth2_getAuthorizationUrl_excludesAttestationParameterWhenNull`
- `oauth2_makeTokenEndpointRequest_includesAttestationParameterWhenNotNull` — captures
  the request and asserts the `attestation` query param is present
- `oauth2_makeTokenEndpointRequest_excludesAttestationParameterWhenNull`
- `oauth2_exchangeCode_sendsAuthorizationCodeParameters` — asserts `client_id`, `code`,
  `code_verifier` present in form body
- `oauth2_swapJWTForTokens_sendsJwtBearerGrantTypeAndAssertion`

**DPoP test pattern to follow:** The `attestation` tests provide the exact pattern for
testing that a header/parameter is (or is not) added. Follow the same mock structure:
capture the outgoing `Request`, assert the `DPoP` header is present (when `useDPoP=true`)
or absent (when `useDPoP=false`).

### 10b. `OAuth2OverrideLoginServerTests.kt`

**Location:** `libs/test/SalesforceSDKTest/src/com/salesforce/androidsdk/auth/OAuth2OverrideLoginServerTests.kt`

**Framework:** Pure JUnit4 (no Android dependencies, no mocks — tests pure logic)

**Naming convention:** `test_given[Precondition]_when[Action]_then[Expected]` (same as iOS)

**Tests:** `overrideLoginServerIfNeeded()` with instance server, null server, community URL,
malformed URL, empty string — good coverage of a pure-logic method.

**Relevance to DPoP:** This test file is a good template for testing pure-logic DPoP helpers
(URL canonicalization, JWT structure parsing, JWK coordinate extraction) — no Android
dependencies, fast to run.

### 10c. `OAuth2Test.java` (integration tests)

**Location:** `libs/test/SalesforceSDKTest/src/com/salesforce/androidsdk/auth/OAuth2Test.java`

Full integration tests requiring a connected device and live credentials (`test_credentials.json`).

### 10d. `AuthenticatorServiceTest.kt` and `AuthenticationUtilitiesTest.kt`

Broader auth flow tests covering login flows, account management, and service interactions.

---

## 11. Test Infrastructure Patterns

### MockK usage in `OAuth2MockTests.kt`

```kotlin
val salesforceSdkManager = mockk<SalesforceSDKManager>(relaxed = true) {
    every { deviceId } returns "__DEVICE_ID__"
    every { appAttestationClient } returns null
}
val requestSlot = slot<Request>()
val httpAccessor = mockk<HttpAccess>(relaxed = true) {
    every { okHttpClient } returns mockk {
        every { newCall(capture(requestSlot)) } returns mockk {
            every { execute() } returns okHttpResponse
        }
    }
}
// ... call the method under test ...
// Assert on requestSlot.captured
```

### Canned response construction

```kotlin
val responseBody = """{"access_token":"t","instance_url":"https://i","id":"https://i/id/o/u"}"""
    .toResponseBody("application/json; charset=utf-8".toMediaType())
val okHttpResponse = mockk<Response>(relaxed = true) {
    every { isSuccessful } returns true
    every { body } returns responseBody
}
```

### DPoP response pattern (to be added)

For testing DPoP `token_type` detection:
```kotlin
val responseBody = """{"access_token":"t","instance_url":"https://i","id":"https://i/id/o/u","token_type":"DPoP"}"""
    .toResponseBody("application/json; charset=utf-8".toMediaType())
```

---

## 12. Phase 2 Implementation Plan

Based on the code analysis, here is the minimum change set for W-22695293:

### New class: `DPoPManager.kt` (new file)
**Location:** `libs/SalesforceSDK/src/com/salesforce/androidsdk/auth/dpop/`

```
Responsibilities:
- generateOrLoadKeyPair(alias: String): KeyPair — Android Keystore EC/P-256
- deleteKeyPair(alias: String)
- buildProof(httpMethod: String, htu: String, nonce: String?, accessToken: String?): String
  → constructs compact JWS: header.payload.signature
  → sign with SHA256withECDSA, convert DER→raw R||S
- getPublicKeyAsJwk(publicKey: ECPublicKey): Map<String, String>
  → { kty: "EC", crv: "P-256", x: <base64url(X)>, y: <base64url(Y)> }
- canonicalHtu(url: String): String  → strip query and fragment
```

### Modified: `OAuth2.java`

**`makeTokenEndpointRequest()` changes:**
1. Add `credentialsIdentifier: String` parameter (threaded from callers)
2. After building the `Request`, if `useDPoP`:
   - Call `DPoPManager.generateOrLoadKeyPair(alias)` to get the key pair
   - Call `DPoPManager.buildProof("POST", htu, nonce=null, accessToken=null)`
   - Add `DPoP` header to request
3. After getting successful response:
   - Parse `token_type` from JSON response
   - Persist on the returned `TokenEndpointResponse`

**`addAuthorizationHeader()` change:**
- Add `tokenType: String?` parameter
- If `tokenType == "DPoP"`: use `"DPoP "` prefix; otherwise `"Bearer "`

**`TokenEndpointResponse` change:**
- Add `public String tokenType;` field
- Parse `token_type` from JSON in the `Response` constructor

### Modified: `SalesforceSDKManager.java`
- Add `private boolean useDPoP = false;`
- Add `public boolean isUseDPoP()` and `public void setUseDPoP(boolean)`

### Modified: `UserAccount` (or equivalent credential storage)
- Add `tokenType` field that's populated from `TokenEndpointResponse.tokenType` after
  successful exchange
- Make it survive serialization/deserialization like other credential fields

### NOT changed in Phase 2:
- `OAuthRefreshInterceptor` (Phase 3)
- Nonce cache or retry logic (Phase 4)
- `ath` claim (Phase 3)

---

## 13. Credential Scope Key for Android DPoP

iOS uses `SFOAuthCredentials.identifier` — a stable string generated before the first
token-endpoint call. On Android, the equivalent stable pre-login identifier needs to be
identified. Candidates:

1. **`clientInfo.userId`** — available after successful login, NOT before
2. **`SalesforceSDKManager.getDeviceId()`** — device-level, not user-level
3. **A generated GUID per login session** — stored alongside the pending credentials

The iOS approach (scoping to `credentials.identifier`) means each user account gets its
own DPoP key pair. For Android, the closest analog is the account name or a UUID generated
at the start of the auth flow and stored with the in-progress credentials.

**This needs to be decided before implementation.** The iOS REFERENCES doc notes that the
scope key must be stable from before the first token-endpoint call.

---

## 14. Related Documentation

- **iOS Implementation Analysis:** See workspace `specs/W-22695293-android-dpop-token-exchange/iOS-IMPLEMENTATION-ANALYSIS.md`
- **Design Spec:** See workspace `specs/W-22606569-dpop-support/dpop-support.md`
- **RFC 9449:** https://datatracker.ietf.org/doc/html/rfc9449
