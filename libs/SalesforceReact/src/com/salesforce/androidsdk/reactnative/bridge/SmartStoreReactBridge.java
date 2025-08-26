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

import android.util.SparseArray;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.reactnative.util.SalesforceReactLogger;
import com.salesforce.androidsdk.smartstore.app.SmartStoreSDKManager;
import com.salesforce.androidsdk.smartstore.store.DBOpenHelper;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.store.StoreCursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SmartStoreReactBridge extends ReactContextBaseJavaModule {

	// Log tag
	static final String TAG = "SmartStoreReactBridge";

	// Keys in json from/to javascript
	static final String RE_INDEX_DATA = "reIndexData";
	static final String CURSOR_ID = "cursorId";
	static final String TYPE = "type";
	static final String SOUP_NAME = "soupName";
	static final String PATH = "path";
	static final String PATHS = "paths";
	static final String QUERY_SPEC = "querySpec";
    static final String SOUP_SPEC = "soupSpec";
    static final String SOUP_SPEC_NAME = "name";
    static final String SOUP_SPEC_FEATURES = "features";
	static final String EXTERNAL_ID_PATH = "externalIdPath";
	static final String ENTRIES = "entries";
	static final String ENTRY_IDS = "entryIds";
	static final String INDEX = "index";
	static final String INDEXES = "indexes";
	static final String IS_GLOBAL_STORE = "isGlobalStore";
	static final String STORE_NAME = "storeName";

	// Map of cursor id to StoreCursor, per database.
	private static Map<SQLiteDatabase, SparseArray<StoreCursor>> STORE_CURSORS = new HashMap<SQLiteDatabase, SparseArray<StoreCursor>>();

	private synchronized static SparseArray<StoreCursor> getSmartStoreCursors(SmartStore store) {
		final SQLiteDatabase db = store.getDatabase();
		if (!STORE_CURSORS.containsKey(db)) {
			STORE_CURSORS.put(db, new SparseArray<StoreCursor>());
		}
		return STORE_CURSORS.get(db);
	}

    public SmartStoreReactBridge(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "SmartStoreReactBridge";
    }

	/**
	 * Native implementation of removeFromSoup
	 * @param args
	 * @param promise
	 */
	    @ReactMethod
	public void removeFromSoup(ReadableMap args, final Promise promise){

		// Parse args
		String soupName = args.getString(SOUP_NAME);

        // Run remove
        try {
            final SmartStore smartStore = getSmartStore(args);
            ReadableArray arraySoupEntryIds = (!args.hasKey(ENTRY_IDS) || args.isNull(ENTRY_IDS) ? null : args.getArray(ENTRY_IDS));
            ReadableMap mapQuerySpec = (!args.hasKey(QUERY_SPEC) || args.isNull(QUERY_SPEC) ? null : args.getMap(QUERY_SPEC));
            if (arraySoupEntryIds != null) {
                List ids = ReactBridgeHelper.toJavaList(arraySoupEntryIds);
                Long[] soupEntryIds = new Long[ids.size()];
                for (int i = 0; i < ids.size(); i++) {
                    soupEntryIds[i] = ((Double) ids.get(i)).longValue();
                }
                smartStore.delete(soupName, soupEntryIds);
            } else {
                JSONObject querySpecJson = new JSONObject(ReactBridgeHelper.toJavaMap(mapQuerySpec));
                QuerySpec querySpec = QuerySpec.fromJSON(soupName, querySpecJson);
                smartStore.deleteByQuery(soupName, querySpec);
            }
            ReactBridgeHelper.resolve(promise);
        } catch (Exception e) {
			SalesforceReactLogger.e(TAG, "removeFromSoup call failed", e);
            ReactBridgeHelper.reject(promise, e.toString());
        }
	}

	/**
	 * Native implementation of retrieveSoupEntries
	 * @param soupName
	 * @param entryIds
	 * @param storeConfig
	 * @param promise
	 */
	    	@ReactMethod
	public void retrieveSoupEntries(String soupName, ReadableArray entryIds, ReadableMap storeConfig, final Promise promise){
		// Run retrieve
		try {
            final SmartStore smartStore = getSmartStore(storeConfig);
            List<String> entryIdsList = ReactBridgeHelper.toJavaStringList(entryIds);
			Long[] soupEntryIds  = new Long[entryIdsList.size()];
			for (int i=0; i<entryIdsList.size(); i++) {
				soupEntryIds[i] = Long.parseLong(entryIdsList.get(i));
			}
			JSONArray result = smartStore.retrieve(soupName, soupEntryIds);
			ReactBridgeHelper.resolve(promise, result);
		} catch (Exception e) {
            SalesforceReactLogger.e(TAG, "retrieveSoupEntries call failed", e);
			ReactBridgeHelper.reject(promise, e.toString());
		}
	}

	/**
	 * Native implementation of closeCursor
	 * @param cursorId
	 * @param storeConfig
	 * @param promise
	 */
	@ReactMethod
	public void closeCursor(String cursorId, ReadableMap storeConfig, final Promise promise){
        try {
            final SmartStore smartStore = getSmartStore(storeConfig);

            // Drop cursor from storeCursors map
            getSmartStoreCursors(smartStore).remove(Integer.parseInt(cursorId));
            ReactBridgeHelper.resolve(promise, "Cursor closed");
        } catch (Exception e) {
            ReactBridgeHelper.reject(promise, e.toString());
        }
	}

	/**
	 * Native implementation of moveCursorToPageIndex
	 * @param cursorId
	 * @param pageIndex
	 * @param storeConfig
	 * @param promise
	 */
	@ReactMethod
	public void moveCursorToPageIndex(String cursorId, int pageIndex, ReadableMap storeConfig, final Promise promise) {
        SmartStore smartStore = null;
        try {
            smartStore = getSmartStore(storeConfig);
        } catch (Exception e) {
            ReactBridgeHelper.reject(promise, e.toString());
            return;
        }

		// Get cursor
		final StoreCursor storeCursor = getSmartStoreCursors(smartStore).get(Integer.parseInt(cursorId));
		if (storeCursor == null) {
			ReactBridgeHelper.reject(promise, "Invalid cursor id");
            return;
		}

		// Change page
		storeCursor.moveToPageIndex(pageIndex);

		// Build json result
		JSONObject result = storeCursor.getDataSerialized(smartStore);
		ReactBridgeHelper.resolve(promise, result);
	}

	/**
	 * Native implementation of soupExists
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void soupExists(String soupName, ReadableMap storeConfig, final Promise promise){
        try {
            final SmartStore smartStore = getSmartStore(storeConfig);

            // Check soup existence
            boolean exists = smartStore.hasSoup(soupName);
            ReactBridgeHelper.resolve(promise, exists);
        } catch (Exception e) {
            ReactBridgeHelper.reject(promise, e.toString());
        }
	}

	/**
	 * Native implementation of upsertSoupEntries
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void upsertSoupEntries(String soupName, ReadableArray entries, Boolean external, ReadableMap storeConfig, final Promise promise){
        SmartStore smartStore = null;
        try {
            smartStore = getSmartStore(storeConfig);
        } catch (Exception e) {
            ReactBridgeHelper.reject(promise, e.toString());
            return;
        }
        
		List entriesList = ReactBridgeHelper.toJavaList(entries);
		boolean isExternal = external != null && external;
		List<JSONObject> entryObjects = new ArrayList<JSONObject>();
		for (int i = 0; i < entriesList.size(); i++) {
			entryObjects.add(new JSONObject((Map) entriesList.get(i)));
		}

		// Run upsert
		synchronized(smartStore.getDatabase()) {
			smartStore.beginTransaction();
			try {
				JSONArray results = new JSONArray();
				for (JSONObject entry : entryObjects) {
					// Use external ID path if external is true, otherwise null for auto-generated IDs
					String externalIdPath = isExternal ? SmartStore.SOUP_ENTRY_ID : null;
					results.put(smartStore.upsert(soupName, entry, externalIdPath, false));
				}
				smartStore.setTransactionSuccessful();
				ReactBridgeHelper.resolve(promise, results);
			} catch (Exception e) {
                SalesforceReactLogger.e(TAG, "upsertSoupEntries call failed", e);
				ReactBridgeHelper.reject(promise, e.toString());
			} finally {
				smartStore.endTransaction();
			}
		}
	}

	/**
	 * Native implementation of registerSoup
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void registerSoup(String soupName, ReadableArray indexSpecs, ReadableMap storeConfig, final Promise promise) {
		try {
			// Parse parameters
			final SmartStore smartStore = getSmartStore(storeConfig);
			IndexSpec[] indexSpecsArray = getIndexSpecsFromArray(indexSpecs);

			smartStore.registerSoup(soupName, indexSpecsArray);
			ReactBridgeHelper.resolve(promise, soupName);
		} catch (Exception e) {
            SalesforceReactLogger.e(TAG, "registerSoup call failed", e);
			ReactBridgeHelper.reject(promise, e.toString());
		}
	}

	/**
	 * Native implementation of querySoup
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void querySoup(String soupName, ReadableMap querySpec, ReadableMap storeConfig, final Promise promise){
		try {
            final SmartStore smartStore = getSmartStore(storeConfig);
            JSONObject querySpecJson = new JSONObject(ReactBridgeHelper.toJavaMap(querySpec));
			QuerySpec querySpecObj = QuerySpec.fromJSON(soupName, querySpecJson);
			if (querySpecObj.queryType == QuerySpec.QueryType.smart) {
				throw new RuntimeException("Smart queries can only be run through runSmartQuery");
			}

			// Run query
			runQuery(smartStore, querySpecObj, promise);
		} catch (Exception e) {
            SalesforceReactLogger.e(TAG, "querySoup call failed", e);
			ReactBridgeHelper.reject(promise, e.toString());
		}
	}

	/**
	 * Native implementation of runSmartSql
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 */
	@ReactMethod
	public void runSmartQuery(ReadableMap querySpec, ReadableMap storeConfig, final Promise promise){
		try {
            final SmartStore smartStore = getSmartStore(storeConfig);
			JSONObject querySpecJson = new JSONObject(ReactBridgeHelper.toJavaMap(querySpec));
			QuerySpec querySpecObj = QuerySpec.fromJSON(null, querySpecJson);
			if (querySpecObj.queryType != QuerySpec.QueryType.smart) {
				throw new RuntimeException("runSmartQuery can only run smart queries");
			}

			// Run query
			runQuery(smartStore, querySpecObj, promise);
		} catch (Exception e) {
            SalesforceReactLogger.e(TAG, "runSmartQuery call failed", e);
			ReactBridgeHelper.reject(promise, e.toString());
		}
	}

	/**
	 * Helper for querySoup and runSmartSql
	 * @param querySpec
	 * @param promise
	 * @throws JSONException
	 */
	private void runQuery(SmartStore smartStore, QuerySpec querySpec,
                         final Promise promise) throws JSONException {

		// Build store cursor
		final StoreCursor storeCursor = new StoreCursor(smartStore, querySpec);
		getSmartStoreCursors(smartStore).put(storeCursor.cursorId, storeCursor);

		// Build json result
		JSONObject result = storeCursor.getDataSerialized(smartStore);

		// Done
        ReactBridgeHelper.resolve(promise, result);
	}

	/**
	 * Native implementation of removeSoup
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void removeSoup(String soupName, ReadableMap storeConfig, final Promise promise) {
        try {
            final SmartStore smartStore = getSmartStore(storeConfig);

            // Run remove
            smartStore.dropSoup(soupName);
            ReactBridgeHelper.resolve(promise, "Soup removed");
        } catch (Exception e) {
            ReactBridgeHelper.reject(promise, e.toString());
        }
	}

	/**
	 * Native implementation of clearSoup
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void clearSoup(String soupName, ReadableMap storeConfig, final Promise promise) {
        try {
            final SmartStore smartStore = getSmartStore(storeConfig);

            // Run clear
            smartStore.clearSoup(soupName);
            ReactBridgeHelper.resolve(promise, "Soup cleared");
        } catch (Exception e) {
            ReactBridgeHelper.reject(promise, e.toString());
        }
	}

	/**
	 * Native implementation of getDatabaseSize
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void getDatabaseSize(ReadableMap storeConfig, final Promise promise) {
        try {
            final SmartStore smartStore = getSmartStore(storeConfig);
            int databaseSize = smartStore.getDatabaseSize();
            JSONObject result = new JSONObject();
            result.put("databaseSize", databaseSize);
            ReactBridgeHelper.resolve(promise, result);
        } catch (Exception e) {
            ReactBridgeHelper.reject(promise, e.toString());
        }
	}

	/**
	 * Native implementation of alterSoup
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void alterSoup(ReadableMap args, final Promise promise) {
		try {

			// Parse args.
			final SmartStore smartStore = getSmartStore(args);
			String soupName = args.getString(SOUP_NAME);
			IndexSpec[] indexSpecs = getIndexSpecsFromArg(args);
			boolean reIndexData = args.getBoolean(RE_INDEX_DATA);

			smartStore.alterSoup(soupName, indexSpecs, reIndexData);
			ReactBridgeHelper.resolve(promise, soupName);
		} catch (Exception e) {
            SalesforceReactLogger.e(TAG, "alterSoup call failed", e);
			ReactBridgeHelper.reject(promise, e.toString());
		}
	}

	/**
	 * Native implementation of reIndexSoup
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void reIndexSoup(ReadableMap args, final Promise promise) {

		// Parse args
		String soupName = args.getString(SOUP_NAME);
        try {
            final SmartStore smartStore = getSmartStore(args);
            List<String> indexPaths = ReactBridgeHelper.toJavaStringList(args.getArray(PATHS));

            // Run register
            smartStore.reIndexSoup(soupName, indexPaths.toArray(new String[0]), true);
            ReactBridgeHelper.resolve(promise, soupName);
        } catch (Exception e) {
            ReactBridgeHelper.reject(promise, e.toString());
        }
	}

	/**
	 * Native implementation of getSoupIndexSpecs
	 * @param args
	 * @param successCallback
     * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void getSoupIndexSpecs(ReadableMap args, final Promise promise) {

		// Get soup index specs
		try {

            // Parse args
            String soupName = args.getString(SOUP_NAME);
            final SmartStore smartStore = getSmartStore(args);
			IndexSpec[] indexSpecs = smartStore.getSoupIndexSpecs(soupName);
			JSONArray indexSpecsJson = new JSONArray();
			for (int i = 0; i < indexSpecs.length; i++) {
				JSONObject indexSpecJson = new JSONObject();
				IndexSpec indexSpec = indexSpecs[i];
				indexSpecJson.put(PATH, indexSpec.path);
				indexSpecJson.put(TYPE, indexSpec.type);
				indexSpecsJson.put(indexSpecJson);
			}
			ReactBridgeHelper.resolve(promise, indexSpecsJson);
		} catch (Exception e) {
            SalesforceReactLogger.e(TAG, "getSoupIndexSpecs call failed", e);
			ReactBridgeHelper.reject(promise, e.toString());
		}
	}

	/**
	 * Native implementation of getAllGlobalStores
	 * @param args
	 * @param successCallback
	 * @param errorCallback
	 * @return
	 */
		@ReactMethod
	public void getAllGlobalStores(final Promise promise) {
		// return list of global store names
		List<String> globalDBNames = SmartStoreSDKManager.getInstance().getGlobalStoresPrefixList();
		JSONArray storeList = new JSONArray();
		if(globalDBNames !=null ) {
			for (String storeName : globalDBNames) {
				storeList.put(storeName);
			}
		}
		ReactBridgeHelper.resolve(promise, storeList);
	}

	/**
	 * Native implementation of getAllStores
	 * @param args
	 * @param successCallback
	 * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void getAllStores(final Promise promise) {
		// return list of store names
		List<String> userStoreNames = SmartStoreSDKManager.getInstance().getUserStoresPrefixList();
		JSONArray storeList = new JSONArray();
		if(userStoreNames !=null ) {
			for (String storeName : userStoreNames) {
				storeList.put(storeName);
			}
		}
		ReactBridgeHelper.resolve(promise, storeList);
	}

	/**
	 * Native implementation of removeStore
	 * @param args
	 * @param successCallback
	 * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void removeStore(ReadableMap args, final Promise promise){

		boolean isGlobal = SmartStoreReactBridge.getIsGlobal(args);
		final String storeName = SmartStoreReactBridge.getStoreName(args);
        if (isGlobal) {
            SmartStoreSDKManager.getInstance().removeGlobalSmartStore(storeName);
            ReactBridgeHelper.resolve(promise, true);
        } else {
            final UserAccount account = UserAccountManager.getInstance().getCachedCurrentUser();
            if (account == null) {
                ReactBridgeHelper.reject(promise, "No user account found");
            }  else {
                SmartStoreSDKManager.getInstance().removeSmartStore(storeName, account, account.getCommunityId());
                ReactBridgeHelper.resolve(promise, true);
            }
        }
	}

	/**
	 * Native implementation of removeAllGlobalStores
	 * @param args
	 * @param successCallback
	 * @param errorCallback
	 * @return
	 */
		@ReactMethod
	public void removeAllGlobalStores(ReadableMap args, final Promise promise) {
		SmartStoreSDKManager.getInstance().removeAllGlobalStores();
		ReactBridgeHelper.resolve(promise, true);
	}

	/**
	 * Native implementation of removeAllStores
	 * @param args
	 * @param successCallback
	 * @param errorCallback
	 * @return
	 */
	@ReactMethod
	public void removeAllStores(ReadableMap args, final Promise promise) {
		SmartStoreSDKManager.getInstance().removeAllUserStores();
		ReactBridgeHelper.resolve(promise, true);
	}

	/**
	 * Return the value of the isGlobalStore argument
	 * @param args
	 * @return
	 */
	private static boolean getIsGlobal(ReadableMap args) {
		return args != null ? args.getBoolean(IS_GLOBAL_STORE) : false;
	}

	/**
	 * Return smartstore to use
	 * @param args arguments passed in bridge call
	 * @return
	 */
	public static SmartStore getSmartStore(ReadableMap args) throws Exception {
		boolean isGlobal = getIsGlobal(args);
		final String storeName = getStoreName(args);
        if (isGlobal) {
            return SmartStoreSDKManager.getInstance().getGlobalSmartStore(storeName);
        } else {
            final UserAccount account = UserAccountManager.getInstance().getCachedCurrentUser();
            if (account == null) {
                throw new Exception("No user account found");
            }  else {
                return SmartStoreSDKManager.getInstance().getSmartStore(storeName, account, account.getCommunityId());
            }
        }
	}

	/**
	 * Return the value of the storename argument
	 * @param args arguments passed in bridge call
	 * @return
	 */
	private static String getStoreName(ReadableMap args) {
		String storeName =  args != null && args.hasKey(STORE_NAME) ? args.getString(STORE_NAME) : DBOpenHelper.DEFAULT_DB_NAME;
		return (storeName!=null && storeName.trim().length()>0) ? storeName : DBOpenHelper.DEFAULT_DB_NAME;
	}

	/**
	 * Build index specs array from javascript argument (legacy)
	 * @param args
	 * @return
	 * @throws JSONException
     */
	private IndexSpec[] getIndexSpecsFromArg(ReadableMap args) throws JSONException {
		JSONArray indexesJson = new JSONArray(ReactBridgeHelper.toJavaList(args.getArray(INDEXES)));
		return IndexSpec.fromJSON(indexesJson);
	}

	/**
	 * Build index specs array from ReadableArray (TurboModule)
	 * @param indexSpecs
	 * @return
	 * @throws JSONException
     */
	private IndexSpec[] getIndexSpecsFromArray(ReadableArray indexSpecs) throws JSONException {
		if (indexSpecs == null) {
			return new IndexSpec[0];
		}
		JSONArray indexesJson = new JSONArray(ReactBridgeHelper.toJavaList(indexSpecs));
		return IndexSpec.fromJSON(indexesJson);
	}
}
