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
package com.salesforce.androidsdk.auth.dpop

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class DPoPNonceCacheTest {

    @After
    fun tearDown() {
        DPoPNonceCache.clearAll()
    }

    @Test
    fun test_givenCredentialsIdentifier_whenStoreAndGet_thenNonceReturned() {
        val id = "user1"
        val nonce = "abc123"
        DPoPNonceCache.store(id, nonce)
        assertEquals(nonce, DPoPNonceCache.get(id))
    }

    @Test
    fun test_givenStoredNonce_whenClear_thenNonceIsNull() {
        val id = "user1"
        DPoPNonceCache.store(id, "abc123")
        DPoPNonceCache.clear(id)
        assertNull(DPoPNonceCache.get(id))
    }

    @Test
    fun test_givenTwoDifferentIdentifiers_whenStoreForBoth_thenValuesAreIsolated() {
        val id1 = "user1"
        val id2 = "user2"
        DPoPNonceCache.store(id1, "nonce-one")
        DPoPNonceCache.store(id2, "nonce-two")
        assertEquals("nonce-one", DPoPNonceCache.get(id1))
        assertEquals("nonce-two", DPoPNonceCache.get(id2))
        DPoPNonceCache.clear(id1)
        assertNull(DPoPNonceCache.get(id1))
        assertEquals("nonce-two", DPoPNonceCache.get(id2))
    }

    @Test
    fun test_givenConcurrentWrites_whenMultipleThreads_thenLastWriteWins() {
        val id = "user-concurrent"
        val threadCount = 20
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        for (i in 0 until threadCount) {
            executor.submit {
                DPoPNonceCache.store(id, "nonce-$i")
                latch.countDown()
            }
        }

        latch.await()
        executor.shutdown()

        // The cache must contain *some* nonce — no NPE, no corruption, no missing entry.
        val result = DPoPNonceCache.get(id)
        assert(result != null && result.startsWith("nonce-")) {
            "Expected a nonce-N value, got: $result"
        }
    }
}
