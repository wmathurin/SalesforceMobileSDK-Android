/*
 * Copyright (c) 2026-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.rest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.dpop.DPoPKeyManager
import com.salesforce.androidsdk.auth.dpop.DPoPNonceCache
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI

/**
 * Tests for DPoP nonce handling in OAuthRefreshInterceptor:
 *   - Proactive caching of DPoP-Nonce header from every response
 *   - Nonce is included in proof when one is cached
 *   - use_dpop_nonce challenge triggers exactly one retry
 *   - No retry occurs for Bearer token type
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class OAuthRefreshInterceptorNonceTest {

    private val credentialsId = "test-credentials-id"
    private val instanceHost = "test.salesforce.com"
    private val authToken = "test-access-token"
    private val dpopKeyAlias = DPoPKeyManager.aliasForCredentialsIdentifier(credentialsId)

    private lateinit var clientInfo: RestClient.ClientInfo

    @Before
    fun setUp() {
        DPoPNonceCache.clearAll()
        DPoPKeyManager.generateOrLoadKeyPair(dpopKeyAlias)

        clientInfo = RestClient.ClientInfo(
            URI.create("https://test.salesforce.com"),
            URI.create("https://login.salesforce.com"),
            URI.create("https://test.salesforce.com/id/orgId/userId"),
            "test@example.com",
            "test@example.com",
            "userId",
            "orgId",
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null
        )

        mockkObject(SalesforceSDKManager)
        val mockSdkManager = mockk<SalesforceSDKManager>(relaxed = true) {
            every { isUseDPoP() } returns true
        }
        every { SalesforceSDKManager.getInstance() } returns mockSdkManager
    }

    @After
    fun tearDown() {
        DPoPNonceCache.clearAll()
        DPoPKeyManager.deleteKeyPair(dpopKeyAlias)
        unmockkAll()
    }

    // ---- helpers ----

    private fun buildInterceptor(tokenType: String = "DPoP"): RestClient.OAuthRefreshInterceptor =
        RestClient.OAuthRefreshInterceptor(clientInfo, authToken, tokenType, credentialsId, null)

    private fun buildRequest(): Request =
        Request.Builder()
            .url("https://test.salesforce.com/services/data/v62.0/query?q=SELECT+Id+FROM+Account")
            .get()
            .build()

    private fun successResponse(request: Request, dpopNonce: String? = null): Response {
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("{}".toResponseBody("application/json".toMediaType()))
        if (dpopNonce != null) builder.header("DPoP-Nonce", dpopNonce)
        return builder.build()
    }

    private fun nonceChallengeResponse(request: Request, nonce: String? = null): Response {
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("""{"error":"use_dpop_nonce"}""".toResponseBody("application/json".toMediaType()))
        if (nonce != null) builder.header("DPoP-Nonce", nonce)
        return builder.build()
    }

    // ---- tests ----

    @Test
    fun test_givenNoCachedNonce_whenRequestSent_thenProofHasNoNonceInCache() {
        val request = buildRequest()
        val interceptor = buildInterceptor()
        val chain = mockk<Interceptor.Chain> {
            every { request() } returns request
            every { proceed(any()) } returns successResponse(request)
        }

        interceptor.intercept(chain)

        // No nonce was in the response, so the cache stays empty.
        assertNull(DPoPNonceCache.get(credentialsId, instanceHost))
    }

    @Test
    fun test_givenSuccessResponseWithDPoPNonceHeader_whenRequestCompletes_thenNonceIsCached() {
        val request = buildRequest()
        val interceptor = buildInterceptor()
        val chain = mockk<Interceptor.Chain> {
            every { request() } returns request
            every { proceed(any()) } returns successResponse(request, dpopNonce = "server-nonce-xyz")
        }

        interceptor.intercept(chain)

        assertEquals("server-nonce-xyz", DPoPNonceCache.get(credentialsId, instanceHost))
    }

    @Test
    fun test_givenCachedNonce_whenRequestSent_thenProofContainsNonceClaim() {
        // Pre-load a nonce so the interceptor picks it up during proof construction.
        DPoPNonceCache.store(credentialsId, instanceHost, "pre-cached-nonce")
        val request = buildRequest()
        val interceptor = buildInterceptor()

        var capturedRequest: Request? = null
        val chain = mockk<Interceptor.Chain> {
            every { request() } returns request
            every { proceed(any()) } answers {
                capturedRequest = firstArg()
                successResponse(firstArg())
            }
        }

        interceptor.intercept(chain)

        val dpopHeader = capturedRequest?.header("DPoP")
        assertNotNull("DPoP header must be present", dpopHeader)
        // Decode payload and verify the nonce claim.
        val parts = dpopHeader!!.split(".")
        assertEquals(3, parts.size)
        val payloadJson = String(
            android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING),
            Charsets.UTF_8
        )
        assert(payloadJson.contains("pre-cached-nonce")) {
            "DPoP proof payload should contain the nonce. Payload: $payloadJson"
        }
    }

    @Test
    fun test_givenUseDPoPNonceError_whenFirstAttempt_thenRetryOnceWithNonce() {
        val serverNonce = "fresh-nonce-from-server"
        val request = buildRequest()
        val interceptor = buildInterceptor()

        var callCount = 0
        val chain = mockk<Interceptor.Chain> {
            every { request() } returns request
            every { proceed(any()) } answers {
                callCount++
                val req: Request = firstArg()
                if (callCount == 1) {
                    nonceChallengeResponse(req, nonce = serverNonce)
                } else {
                    successResponse(req)
                }
            }
        }

        interceptor.intercept(chain)

        // chain.proceed must be called exactly twice: initial + one retry.
        assertEquals(2, callCount)
        // The nonce was stored during the challenge response.
        assertEquals(serverNonce, DPoPNonceCache.get(credentialsId, instanceHost))
    }

    @Test
    fun test_givenUseDPoPNonceError_whenSecondAttemptAlsoFails_thenNoFurtherRetry() {
        val request = buildRequest()
        val interceptor = buildInterceptor()

        var callCount = 0
        val chain = mockk<Interceptor.Chain> {
            every { request() } returns request
            every { proceed(any()) } answers {
                callCount++
                nonceChallengeResponse(firstArg(), nonce = "nonce-$callCount")
            }
        }

        interceptor.intercept(chain)

        // Only two calls: the nonce challenge path retries at most once;
        // the second call is a 401 but falls through to the standard refresh path
        // (which won't retry further since authTokenProvider is null here).
        assert(callCount <= 2) {
            "Expected at most 2 chain.proceed calls, got $callCount"
        }
    }

    @Test
    fun test_givenBearerTokenType_whenUseDPoPNonceResponseReceived_thenNoRetry() {
        val request = buildRequest()
        val interceptor = buildInterceptor(tokenType = "Bearer")

        var callCount = 0
        val chain = mockk<Interceptor.Chain> {
            every { request() } returns request
            every { proceed(any()) } answers {
                callCount++
                nonceChallengeResponse(firstArg())
            }
        }

        interceptor.intercept(chain)

        // Bearer type must not trigger the DPoP nonce retry path.
        assertEquals(1, callCount)
        // Nonce must not be cached for Bearer type.
        assertNull(DPoPNonceCache.get(credentialsId, instanceHost))
    }
}
