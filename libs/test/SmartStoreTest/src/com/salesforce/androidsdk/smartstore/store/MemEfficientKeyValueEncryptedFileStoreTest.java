/*
 * Copyright (c) 2025-present, salesforce.com, inc.
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
 * specific prior written permission.
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
package com.salesforce.androidsdk.smartstore.store;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.salesforce.androidsdk.analytics.security.Encryptor;
import com.salesforce.androidsdk.app.SalesforceSDKManager;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Tests for MemEfficientKeyValueEncryptedFileStore
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class MemEfficientKeyValueEncryptedFileStoreTest {

    private static final String TEST_STORE = "test_store";

    private MemEfficientKeyValueEncryptedFileStore store;
    private File storeDir;

    @Before
    public void setUp() throws Exception {
        storeDir = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(), TEST_STORE);
        store = new MemEfficientKeyValueEncryptedFileStore(InstrumentationRegistry.getInstrumentation().getTargetContext(), TEST_STORE, SalesforceSDKManager.getEncryptionKey());
    }

    @After
    public void tearDown() throws Exception {
        if (store != null) {
            store.deleteAll();
        }
        if (storeDir != null && storeDir.exists()) {
            deleteRecursively(storeDir);
        }
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    /**
     * Helper method to create a large string stream without materializing the entire string in memory.
     * This generates a repeating pattern of characters to create predictable, large content.
     */
    private InputStream getLargeStringStream(long sizeInBytes) {
        return new InputStream() {
            private long bytesRead = 0;
            private final String pattern = "This is a test string pattern that repeats to create large content. ";
            private int patternIndex = 0;

            @Override
            public int read() throws IOException {
                if (bytesRead >= sizeInBytes) {
                    return -1; // End of stream
                }
                
                byte b = (byte) pattern.charAt(patternIndex);
                patternIndex = (patternIndex + 1) % pattern.length();
                bytesRead++;
                return b & 0xFF; // Convert to unsigned byte
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                if (bytesRead >= sizeInBytes) {
                    return -1;
                }
                
                int bytesToRead = (int) Math.min(length, sizeInBytes - bytesRead);
                for (int i = 0; i < bytesToRead; i++) {
                    buffer[offset + i] = (byte) pattern.charAt(patternIndex);
                    patternIndex = (patternIndex + 1) % pattern.length();
                }
                bytesRead += bytesToRead;
                return bytesToRead;
            }
        };
    }

    /**
     * Helper method to compare two streams for equality without loading them entirely into memory.
     */
    private boolean streamsEqual(InputStream stream1, InputStream stream2) throws IOException {
        byte[] buffer1 = new byte[8192];
        byte[] buffer2 = new byte[8192];
        
        int bytesRead1, bytesRead2;
        while (true) {
            bytesRead1 = stream1.read(buffer1);
            bytesRead2 = stream2.read(buffer2);
            
            if (bytesRead1 != bytesRead2) {
                return false;
            }
            
            if (bytesRead1 == -1) {
                return true; // Both streams ended
            }
            
            for (int i = 0; i < bytesRead1; i++) {
                if (buffer1[i] != buffer2[i]) {
                    return false;
                }
            }
        }
    }

    /**
     * Helper method to convert stream to string.
     */
    private String streamToString(InputStream inputStream) {
        if (inputStream == null) {
            return null;
        }
        try {
            return Encryptor.getStringFromStream(inputStream);
        } catch (IOException e) {
            Assert.fail("Failed to read from stream");
            return null;
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                Assert.fail("Stream failed to close");
            }
        }
    }

    /**
     * Helper method to generate test data of a specified size.
     */
    private String generateTestData(int size, String pattern) {
        StringBuilder sb = new StringBuilder(size);
        while (sb.length() < size) {
            sb.append(pattern);
        }
        return sb.substring(0, size);
    }

    @Test
    public void testSaveLargeStreamGetLargeStream() throws Exception {
        final String key = "large_stream_key";
        final long streamSize = 16 * 1024 * 1024; // 50MB
        
        // Save large stream
        InputStream inputStream = getLargeStringStream(streamSize);
        boolean saveResult = store.saveStream(key, inputStream);
        Assert.assertTrue("Failed to save large stream", saveResult);
        
        // Retrieve and verify stream
        InputStream retrievedStream = store.getStream(key);
        Assert.assertNotNull("Retrieved stream should not be null", retrievedStream);
        
        // Compare streams without loading into memory
        InputStream expectedStream = getLargeStringStream(streamSize);
        boolean streamsMatch = streamsEqual(expectedStream, retrievedStream);
        Assert.assertTrue("Retrieved stream content should match original", streamsMatch);
        
        // Clean up
        retrievedStream.close();
        expectedStream.close();
        inputStream.close();
    }

    @Test
    public void testPerformanceComparisonStringVsStream() throws Exception {
        final int numEntries = 1024;
        final int[] stringSizes = {2048, 4096, 8192, 16384, 32768, 65536, 131072, 262144}; // 2KB to 256KB
        
        long totalStringWriteTime = 0;
        long totalStringReadTime = 0;
        long totalStreamWriteTime = 0;
        long totalStreamReadTime = 0;
        
        for (int i = 0; i < numEntries; i++) {
            String key = "perf_test_key_" + i;
            int sizeIndex = i % stringSizes.length;
            int stringSize = stringSizes[sizeIndex];
            
            // Create test data
            String testData = generateTestData(stringSize, "Test data pattern for performance comparison. ");
            
            // Test string-based API
            long startTime = System.nanoTime();
            store.saveValue(key + "_string", testData);
            long endTime = System.nanoTime();
            totalStringWriteTime += (endTime - startTime);
            
            startTime = System.nanoTime();
            String retrievedString = store.getValue(key + "_string");
            endTime = System.nanoTime();
            totalStringReadTime += (endTime - startTime);
            
            Assert.assertEquals("String data should match", testData, retrievedString);
            
            // Test stream-based API
            InputStream testStream = new ByteArrayInputStream(testData.getBytes());
            startTime = System.nanoTime();
            store.saveStream(key + "_stream", testStream);
            endTime = System.nanoTime();
            totalStreamWriteTime += (endTime - startTime);
            
            startTime = System.nanoTime();
            InputStream retrievedStream = store.getStream(key + "_stream");
            endTime = System.nanoTime();
            totalStreamReadTime += (endTime - startTime);
            
            Assert.assertNotNull("Stream should not be null", retrievedStream);
            String streamContent = streamToString(retrievedStream);
            Assert.assertEquals("Stream data should match", testData, streamContent);
            
            // Clean up streams
            testStream.close();
        }
        
        // Convert to milliseconds for readability
        double stringWriteTimeMs = totalStringWriteTime / 1_000_000.0;
        double stringReadTimeMs = totalStringReadTime / 1_000_000.0;
        double streamWriteTimeMs = totalStreamWriteTime / 1_000_000.0;
        double streamReadTimeMs = totalStreamReadTime / 1_000_000.0;
        
        System.out.println("Performance Results for " + numEntries + " entries:");
        System.out.println("String Write Time: " + String.format("%.2f", stringWriteTimeMs) + " ms");
        System.out.println("String Read Time: " + String.format("%.2f", stringReadTimeMs) + " ms");
        System.out.println("Stream Write Time: " + String.format("%.2f", streamWriteTimeMs) + " ms");
        System.out.println("Stream Read Time: " + String.format("%.2f", streamReadTimeMs) + " ms");
        
        // Performance assertions - streaming should be reasonably close to string performance
        // Allow up to 3x slower for streaming due to cipher overhead
        Assert.assertTrue("Stream write performance should be reasonable", 
            streamWriteTimeMs < stringWriteTimeMs * 3);
        Assert.assertTrue("Stream read performance should be reasonable", 
            streamReadTimeMs < stringReadTimeMs * 3);
    }

    @Test
    public void testCrossCompatibilityStringVsStream() throws Exception {
        final int stringSize = 32 * 1024; // 32KB
        
        // Create test data
        String testData = generateTestData(stringSize, "Cross-compatibility test data pattern. ");
        
        // Test 1: Save with string API, read with stream API
        store.saveValue("string_key", testData);
        InputStream stringAsStream = store.getStream("string_key");
        Assert.assertNotNull("String data should be readable as stream", stringAsStream);
        
        String streamContent = streamToString(stringAsStream);
        Assert.assertEquals("String->Stream conversion should preserve data", testData, streamContent);
        
        // Test 2: Save with stream API, read with string API
        InputStream testStream = new ByteArrayInputStream(testData.getBytes());
        store.saveStream("stream_key", testStream);
        testStream.close();
        
        String streamAsString = store.getValue("stream_key");
        Assert.assertEquals("Stream->String conversion should preserve data", testData, streamAsString);
    }

    @Test
    public void testCrossCompatibilityRegularVsMemEfficient() throws Exception {
        final int stringSize = 32 * 1024; // 32KB
        
        // Create test data
        String testData = generateTestData(stringSize, "Regular vs MemEfficient cross-compatibility test data. ");
        
        // Create a regular store that uses the same directory as the MemEfficient store
        KeyValueEncryptedFileStore regularStore = new KeyValueEncryptedFileStore(
            InstrumentationRegistry.getInstrumentation().getTargetContext(), TEST_STORE, SalesforceSDKManager.getEncryptionKey());
        
        try {
            // Test 1: regular.saveValue -> store.getValue
            regularStore.saveValue("test1", testData);
            String result1 = store.getValue("test1");
            Assert.assertEquals("regular.saveValue -> store.getValue should work", testData, result1);
            
            // Test 2: regular.saveValue -> store.getStream
            regularStore.saveValue("test2", testData);
            InputStream stream2 = store.getStream("test2");
            Assert.assertNotNull("regular.saveValue -> store.getStream should return stream", stream2);
            String result2 = streamToString(stream2);
            Assert.assertEquals("regular.saveValue -> store.getStream should match", testData, result2);
            
            // Test 3: regular.saveStream -> store.getValue
            InputStream inputStream3 = new ByteArrayInputStream(testData.getBytes());
            regularStore.saveStream("test3", inputStream3);
            inputStream3.close();
            String result3 = store.getValue("test3");
            Assert.assertEquals("regular.saveStream -> store.getValue should work", testData, result3);
            
            // Test 4: regular.saveStream -> store.getStream
            InputStream inputStream4 = new ByteArrayInputStream(testData.getBytes());
            regularStore.saveStream("test4", inputStream4);
            inputStream4.close();
            InputStream stream4 = store.getStream("test4");
            Assert.assertNotNull("regular.saveStream -> store.getStream should return stream", stream4);
            String result4 = streamToString(stream4);
            Assert.assertEquals("regular.saveStream -> store.getStream should match", testData, result4);
            
            // Test 5: store.saveValue -> regular.getValue
            store.saveValue("test5", testData);
            String result5 = regularStore.getValue("test5");
            Assert.assertEquals("store.saveValue -> regular.getValue should work", testData, result5);
            
            // Test 6: store.saveValue -> regular.getStream
            store.saveValue("test6", testData);
            InputStream stream6 = regularStore.getStream("test6");
            Assert.assertNotNull("store.saveValue -> regular.getStream should return stream", stream6);
            String result6 = streamToString(stream6);
            Assert.assertEquals("store.saveValue -> regular.getStream should match", testData, result6);
            
            // Test 7: store.saveStream -> regular.getValue
            InputStream inputStream7 = new ByteArrayInputStream(testData.getBytes());
            store.saveStream("test7", inputStream7);
            inputStream7.close();
            String result7 = regularStore.getValue("test7");
            Assert.assertEquals("store.saveStream -> regular.getValue should work", testData, result7);
            
            // Test 8: store.saveStream -> regular.getStream
            InputStream inputStream8 = new ByteArrayInputStream(testData.getBytes());
            store.saveStream("test8", inputStream8);
            inputStream8.close();
            InputStream stream8 = regularStore.getStream("test8");
            Assert.assertNotNull("store.saveStream -> regular.getStream should return stream", stream8);
            String result8 = streamToString(stream8);
            Assert.assertEquals("store.saveStream -> regular.getStream should match", testData, result8);
            
        } finally {
            regularStore.deleteAll();
        }
    }
}
