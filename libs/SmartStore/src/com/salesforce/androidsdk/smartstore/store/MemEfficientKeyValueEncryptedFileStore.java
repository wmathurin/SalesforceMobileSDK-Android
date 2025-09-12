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

package com.salesforce.androidsdk.smartstore.store;

import android.content.Context;

import com.salesforce.androidsdk.analytics.security.Encryptor;
import com.salesforce.androidsdk.smartstore.util.SmartStoreLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;

/**
 * Memory-efficient version of KeyValueEncryptedFileStore that uses streaming encryption/decryption
 * to avoid loading entire files into memory. This prevents OutOfMemoryError for very large values
 * at the cost of some performance overhead due to streaming cipher operations.
 */
public class MemEfficientKeyValueEncryptedFileStore extends KeyValueEncryptedFileStore {

    private static final String TAG = MemEfficientKeyValueEncryptedFileStore.class.getSimpleName();

    /**
     * Constructor
     *
     * @param ctx context
     * @param storeName name for key value store
     * @param encryptionKey encryption key for key value store
     */
    public MemEfficientKeyValueEncryptedFileStore(Context ctx, String storeName, String encryptionKey) {
        super(ctx, storeName, encryptionKey);
    }

    /**
     * Constructor
     *
     * @param parentDir parent directory for key value store
     * @param storeName name for key value store
     * @param encryptionKey encryption key for key value store
     */
    MemEfficientKeyValueEncryptedFileStore(File parentDir, String storeName, String encryptionKey) {
        super(parentDir, storeName, encryptionKey);
    }

    /**
     * Save value given as an input stream for the given key using streaming encryption.
     * NB: does not close provided input stream
     *
     * @param key Unique identifier.
     * @param stream Stream to be persisted.
     * @return True - if successful, False - otherwise.
     */
    @Override
    public boolean saveStream(String key, InputStream stream) {
        if (!isKeyValid(key, "saveStream")) {
            return false;
        }
        if (stream == null) {
            SmartStoreLogger.w(TAG, "saveStream: Invalid stream supplied: null");
            return false;
        }
        try {
            if (kvVersion >= 2) encryptStringToFile(getKeyFile(key), key, encryptionKey);
            // Use streaming version to avoid loading entire stream into memory
            encryptStreamToFileStreaming(getValueFile(key), stream, encryptionKey);
            return true;
        } catch (Exception e) {
            SmartStoreLogger.e(TAG, "Exception occurred while saving stream to filesystem", e);
            return false;
        }
    }

    /**
     * Returns stream for value of given key using streaming decryption.
     *
     * @param key Unique identifier.
     * @return stream to value for given key or null if key not found.
     */
    @Override
    public InputStream getStream(String key) {
        if (!isKeyValid(key, "getStream")) {
            return null;
        }
        final File file = getValueFile(key);
        if (!file.exists()) {
            SmartStoreLogger.w(TAG, "getStream: File does not exist for key: " + key);
            return null;
        }
        try {
            // Use streaming version to avoid loading entire file into memory
            return decryptFileAsStreamStreaming(file, encryptionKey);
        } catch (Exception e) {
            SmartStoreLogger.e(TAG, "getStream: Threw exception for key: " + key, e);
            return null;
        }
    }

    /**
     * Streaming version of encryptStreamToFile that doesn't load entire stream into memory.
     * Uses CipherOutputStream to encrypt data as it's being written.
     */
    private void encryptStreamToFileStreaming(File file, InputStream stream, String encryptionKey) throws IOException {
        FileOutputStream fileOut = null;
        CipherOutputStream cipherOut = null;
        try {
            // Get encrypting cipher with a new IV
            Cipher cipher = Encryptor.getEncryptingCipher(encryptionKey);
            
            // Write IV to the beginning of the file
            fileOut = new FileOutputStream(file);
            byte[] iv = cipher.getIV();
            fileOut.write(iv);
            
            // Create cipher output stream for streaming encryption
            cipherOut = new CipherOutputStream(fileOut, cipher);
            
            // Stream data through cipher
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = stream.read(buffer)) != -1) {
                cipherOut.write(buffer, 0, bytesRead);
            }
        } catch (InvalidAlgorithmParameterException | InvalidKeyException e) {
            throw new IOException("Failed to initialize cipher for encryption", e);
        } finally {
            if (cipherOut != null) {
                cipherOut.close();
            } else if (fileOut != null) {
                fileOut.close();
            }
        }
    }

    /**
     * Streaming version of decryptFileAsStream that doesn't load entire file into memory.
     * Uses CipherInputStream to decrypt data as it's being read.
     */
    private InputStream decryptFileAsStreamStreaming(File file, String encryptionKey) throws IOException {
        try {
            FileInputStream fileIn = new FileInputStream(file);
            
            // Read IV from the beginning of the file
            byte[] iv = new byte[12]; // AES-GCM uses 12-byte IV
            int bytesRead = fileIn.read(iv);
            if (bytesRead != 12) {
                fileIn.close();
                throw new IOException("Failed to read IV from encrypted file");
            }
            
            // Get decrypting cipher with the IV
            Cipher cipher = Encryptor.getDecryptingCipher(encryptionKey, iv);
            
            // Return cipher input stream for streaming decryption
            return new CipherInputStream(fileIn, cipher);
        } catch (InvalidAlgorithmParameterException | InvalidKeyException e) {
            throw new IOException("Failed to initialize cipher for decryption", e);
        }
    }
}
