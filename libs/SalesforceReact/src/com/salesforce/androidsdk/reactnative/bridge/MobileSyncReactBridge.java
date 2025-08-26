/*
 * Copyright (c) 2015-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.reactnative.bridge;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.salesforce.androidsdk.mobilesync.manager.SyncManager;
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget;
import com.salesforce.androidsdk.mobilesync.target.SyncUpTarget;
import com.salesforce.androidsdk.mobilesync.util.SyncOptions;
import com.salesforce.androidsdk.mobilesync.util.SyncState;
import com.salesforce.androidsdk.reactnative.util.SalesforceReactLogger;
import com.salesforce.androidsdk.smartstore.store.SmartStore;

import org.json.JSONObject;

public class MobileSyncReactBridge extends ReactContextBaseJavaModule {

    // Keys in json from/to javascript
    static final String TARGET = "target";
    static final String SOUP_NAME = "soupName";
    static final String OPTIONS = "options";
    static final String SYNC_ID = "syncId";
    static final String SYNC_NAME = "syncName";
    public static final String TAG = "MobileSyncReactBridge";

    public MobileSyncReactBridge(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return TAG;
    }

    /**
     * Native implementation of syncUp
     * @param target
     * @param soupName
     * @param options
     * @param syncName
     * @param storeConfig
     * @param promise
     */
    @ReactMethod
    public void syncUp(ReadableMap target, String soupName, ReadableMap options, String syncName, ReadableMap storeConfig, final Promise promise) {
        try {
            // Parse parameters
            JSONObject targetJson = new JSONObject(ReactBridgeHelper.toJavaMap(target));
            JSONObject optionsJson = new JSONObject(ReactBridgeHelper.toJavaMap(options));
            final SyncManager syncManager = getSyncManager(storeConfig);
            
            syncManager.syncUp(SyncUpTarget.fromJSON(targetJson), SyncOptions.fromJSON(optionsJson), soupName, syncName, new SyncManager.SyncUpdateCallback() {
                @Override
                public void onUpdate(SyncState sync) {
                    handleSyncUpdate(sync, promise);
                }
            });
        } catch (Exception e) {
            SalesforceReactLogger.e(TAG, "syncUp call failed", e);
            ReactBridgeHelper.reject(promise, e.toString());
        }
    }

    /**
     * Native implementation of syncDown
     * @param target
     * @param soupName
     * @param options
     * @param syncName
     * @param storeConfig
     * @param promise
     */
    @ReactMethod
    public void syncDown(ReadableMap target, String soupName, ReadableMap options, String syncName, ReadableMap storeConfig, final Promise promise) {
        try {
            // Parse parameters
            JSONObject targetJson = new JSONObject(ReactBridgeHelper.toJavaMap(target));
            JSONObject optionsJson = new JSONObject(ReactBridgeHelper.toJavaMap(options));
            final SyncManager syncManager = getSyncManager(storeConfig);
            
            syncManager.syncDown(SyncDownTarget.fromJSON(targetJson), SyncOptions.fromJSON(optionsJson), soupName, syncName, new SyncManager.SyncUpdateCallback() {
                @Override
                public void onUpdate(SyncState sync) {
                    handleSyncUpdate(sync, promise);
                }
            });
        } catch (Exception e) {
            SalesforceReactLogger.e(TAG, "syncDown call failed", e);
            ReactBridgeHelper.reject(promise, e.toString());
        }
    }

    /**
     * Native implementation of getSyncStatus
     * @param syncId
     * @param storeConfig
     * @param promise
     */
    @ReactMethod
    public void getSyncStatus(int syncId, ReadableMap storeConfig, final Promise promise) {
        try {
            final SyncManager syncManager = getSyncManager(storeConfig);
            SyncState sync = syncManager.getSyncStatus(syncId);
            ReactBridgeHelper.resolve(promise, sync == null ? null : sync.asJSON());
        } catch (Exception e) {
            SalesforceReactLogger.e(TAG, "getSyncStatus call failed", e);
            ReactBridgeHelper.reject(promise, e.toString());
        }
    }

    /**
     * Native implementation of deleteSync
     * @param syncId
     * @param storeConfig
     * @param promise
     */
    @ReactMethod
    public void deleteSync(int syncId, ReadableMap storeConfig, final Promise promise) {
        try {
            final SyncManager syncManager = getSyncManager(storeConfig);
            syncManager.deleteSync(syncId);
            ReactBridgeHelper.resolve(promise, "Sync deleted");
        } catch (Exception e) {
            SalesforceReactLogger.e(TAG, "deleteSync call failed", e);
            ReactBridgeHelper.reject(promise, e.toString());
        }
    }

    /**
     * Native implementation of reSync
     * @param syncId
     * @param storeConfig
     * @param promise
     */
    @ReactMethod
    public void reSync(int syncId, ReadableMap storeConfig, final Promise promise) {
        try {
            final SyncManager syncManager = getSyncManager(storeConfig);
            SyncManager.SyncUpdateCallback callback = new SyncManager.SyncUpdateCallback() {
                @Override
                public void onUpdate(SyncState sync) {
                    handleSyncUpdate(sync, promise);
                }
            };

            syncManager.reSync(syncId, callback);
        } catch (Exception e) {
            SalesforceReactLogger.e(TAG, "reSync call failed", e);
            ReactBridgeHelper.reject(promise, e.toString());
        }
    }

    /**
     * Native implementation of cleanResyncGhosts
     * @param syncId
     * @param storeConfig
     * @param promise
     */
    @ReactMethod
    public void cleanResyncGhosts(int syncId, ReadableMap storeConfig, final Promise promise) {
        try {
            final SyncManager syncManager = getSyncManager(storeConfig);
            syncManager.cleanResyncGhosts(syncId, new SyncManager.CleanResyncGhostsCallback() {
                @Override
                public void onSuccess(int numRecords) {
                    ReactBridgeHelper.resolve(promise, "Cleaned " + numRecords + " ghost records");
                }

                @Override
                public void onError(Exception e) {
                    ReactBridgeHelper.reject(promise, e.toString());
                }
            });
        } catch (Exception e) {
            SalesforceReactLogger.e(TAG, "cleanResyncGhosts call failed", e);
            ReactBridgeHelper.reject(promise, e.toString());
        }
    }

    /**
     * Sync update handler
     * @param sync
     * @param promise
     */
    private void handleSyncUpdate(final SyncState sync, Promise promise) {
        try {
            switch (sync.getStatus()) {
                case NEW:
                    break;
                case RUNNING:
                    break;
                case DONE:
                    ReactBridgeHelper.resolve(promise, sync.asJSON());
                    break;
                case FAILED:
                    //Return sync to React Native with the error message in the JSON
                    ReactBridgeHelper.reject(promise, sync.asJSON().toString());
                    break;
            }
        } catch (Exception e) {
            SalesforceReactLogger.e(TAG, "handleSyncUpdate call failed", e);
        }
    }

    /**
     * Return sync manager to use
     * @param storeConfig Store configuration
     * @return
     */
    private SyncManager getSyncManager(ReadableMap storeConfig) throws Exception {
        final SmartStore smartStore = SmartStoreReactBridge.getSmartStore(storeConfig);
        final SyncManager syncManager = SyncManager.getInstance(null, null, smartStore);
        return syncManager;
    }
}
