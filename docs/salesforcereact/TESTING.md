# SalesforceReact Testing Guide

This guide covers testing strategies, patterns, and best practices for the SalesforceReact library.

## Table of Contents

- [Overview](#overview)
- [Test Structure](#test-structure)
- [Testing Bridge Modules](#testing-bridge-modules)
- [Testing Authentication Flow](#testing-authentication-flow)
- [Testing SmartStore Integration](#testing-smartstore-integration)
- [Testing MobileSync](#testing-mobilesync)
- [Testing Activity Lifecycle](#testing-activity-lifecycle)
- [Integration Testing](#integration-testing)
- [Common Test Patterns](#common-test-patterns)
- [Test Utilities](#test-utilities)

## Overview

The SalesforceReact library sits at the intersection of React Native and Salesforce Mobile SDK. Testing requires consideration of:

- **React Native bridge communication**: Callback invocation and data serialization
- **Salesforce SDK integration**: Authentication, REST client, SmartStore, MobileSync
- **Android lifecycle**: Activity states, configuration changes, process death
- **Asynchronous operations**: Network calls, database operations, OAuth flow

## Test Structure

### Current State

As of this writing, the SalesforceReact library does not have a dedicated test suite in the repository. Tests would typically be organized as:

```
SalesforceReact/
├── src/                          # Production code
│   └── com/salesforce/androidsdk/reactnative/
└── androidTest/                  # Instrumented tests (recommended location)
    └── com/salesforce/androidsdk/reactnative/
        ├── app/
        │   ├── SalesforceReactSDKManagerTest.java
        │   └── SalesforceReactUpgradeManagerTest.java
        ├── bridge/
        │   ├── SalesforceOauthReactBridgeTest.java
        │   ├── SmartStoreReactBridgeTest.java
        │   ├── MobileSyncReactBridgeTest.java
        │   ├── SalesforceNetReactBridgeTest.java
        │   └── ReactBridgeHelperTest.java
        ├── ui/
        │   ├── SalesforceReactActivityTest.java
        │   └── SalesforceReactActivityDelegateTest.java
        └── util/
            └── SalesforceReactLoggerTest.java
```

### Test Dependencies

Tests would require:

```kotlin
// build.gradle.kts
dependencies {
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("org.mockito:mockito-android:5.3.1")
    androidTestImplementation("com.facebook.react:react-native:+")
    
    // Salesforce SDK test dependencies
    androidTestImplementation(project(":libs:SalesforceSDK"))
    androidTestImplementation(project(":libs:SmartStore"))
    androidTestImplementation(project(":libs:MobileSync"))
}
```

## Testing Bridge Modules

Bridge modules are the primary interface between JavaScript and native code. Tests should verify:

1. Correct callback invocation (success vs error)
2. Data type conversion (ReadableMap/Array → Java types)
3. Integration with SDK components
4. Error handling

### Example: Testing SalesforceOauthReactBridge

```java
@RunWith(AndroidJUnit4.class)
public class SalesforceOauthReactBridgeTest {
    
    private SalesforceOauthReactBridge bridge;
    private ReactApplicationContext reactContext;
    private SalesforceReactActivity mockActivity;
    
    @Before
    public void setUp() {
        reactContext = mock(ReactApplicationContext.class);
        mockActivity = mock(SalesforceReactActivity.class);
        when(reactContext.getCurrentActivity()).thenReturn(mockActivity);
        
        bridge = new SalesforceOauthReactBridge(reactContext);
    }
    
    @Test
    public void test_givenNoActivity_whenAuthenticate_thenErrorCallback() {
        // Given: No current activity
        when(reactContext.getCurrentActivity()).thenReturn(null);
        
        Callback successCallback = mock(Callback.class);
        Callback errorCallback = mock(Callback.class);
        ReadableMap args = mock(ReadableMap.class);
        
        // When: authenticate called
        bridge.authenticate(args, successCallback, errorCallback);
        
        // Then: Error callback invoked
        verify(errorCallback).invoke("SalesforceReactActivity not found");
        verify(successCallback, never()).invoke(any());
    }
    
    @Test
    public void test_givenActivity_whenAuthenticate_thenDelegatesToActivity() {
        // Given: Valid activity
        Callback successCallback = mock(Callback.class);
        Callback errorCallback = mock(Callback.class);
        ReadableMap args = mock(ReadableMap.class);
        
        // When: authenticate called
        bridge.authenticate(args, successCallback, errorCallback);
        
        // Then: Activity authenticate method called
        verify(mockActivity).authenticate(successCallback, errorCallback);
    }
    
    @Test
    public void test_givenNotAuthenticated_whenGetAuthCredentials_thenErrorCallback() {
        // Given: No authenticated client
        doAnswer(invocation -> {
            Callback errorCallback = invocation.getArgument(1);
            errorCallback.invoke("Not authenticated");
            return null;
        }).when(mockActivity).getAuthCredentials(any(), any());
        
        Callback successCallback = mock(Callback.class);
        Callback errorCallback = mock(Callback.class);
        ReadableMap args = mock(ReadableMap.class);
        
        // When: getAuthCredentials called
        bridge.getAuthCredentials(args, successCallback, errorCallback);
        
        // Then: Error callback invoked
        verify(errorCallback).invoke("Not authenticated");
    }
}
```

### Example: Testing SmartStoreReactBridge

```java
@RunWith(AndroidJUnit4.class)
public class SmartStoreReactBridgeTest {
    
    private SmartStoreReactBridge bridge;
    private ReactApplicationContext reactContext;
    private SmartStore mockStore;
    
    @Before
    public void setUp() {
        reactContext = mock(ReactApplicationContext.class);
        mockStore = mock(SmartStore.class);
        
        // Mock SmartStoreSDKManager to return mock store
        SmartStoreSDKManager mockManager = mock(SmartStoreSDKManager.class);
        when(mockManager.getSmartStore(any(), any(), any())).thenReturn(mockStore);
        
        bridge = new SmartStoreReactBridge(reactContext);
    }
    
    @Test
    public void test_givenValidSoup_whenRegisterSoup_thenSuccessCallback() throws Exception {
        // Given: Valid soup registration
        ReadableMap args = createMockReadableMap(
            "soupName", "accounts",
            "indexes", createIndexArray("Id", "Name"),
            "isGlobalStore", false
        );
        
        Callback successCallback = mock(Callback.class);
        Callback errorCallback = mock(Callback.class);
        
        // When: registerSoup called
        bridge.registerSoup(args, successCallback, errorCallback);
        
        // Then: SmartStore registerSoup called and success callback invoked
        verify(mockStore).registerSoup(eq("accounts"), any(IndexSpec[].class));
        verify(successCallback).invoke(contains("accounts"));
        verify(errorCallback, never()).invoke(any());
    }
    
    @Test
    public void test_givenEntries_whenUpsertSoupEntries_thenTransactionUsed() throws Exception {
        // Given: Entries to upsert
        JSONObject entry1 = new JSONObject().put("Id", "001").put("Name", "Acme");
        JSONObject entry2 = new JSONObject().put("Id", "002").put("Name", "Beta");
        
        ReadableArray entries = createReadableArray(entry1, entry2);
        ReadableMap args = createMockReadableMap(
            "soupName", "accounts",
            "entries", entries,
            "externalIdPath", "Id"
        );
        
        when(mockStore.getDatabase()).thenReturn(mock(SQLiteDatabase.class));
        when(mockStore.upsert(any(), any(), any(), anyBoolean())).thenReturn(entry1, entry2);
        
        Callback successCallback = mock(Callback.class);
        Callback errorCallback = mock(Callback.class);
        
        // When: upsertSoupEntries called
        bridge.upsertSoupEntries(args, successCallback, errorCallback);
        
        // Then: Transaction boundaries used
        verify(mockStore).beginTransaction();
        verify(mockStore, times(2)).upsert(eq("accounts"), any(), eq("Id"), eq(false));
        verify(mockStore).setTransactionSuccessful();
        verify(mockStore).endTransaction();
        verify(successCallback).invoke(any());
    }
    
    @Test
    public void test_givenQuerySpec_whenQuerySoup_thenCursorReturned() throws Exception {
        // Given: Valid query spec
        ReadableMap querySpec = createMockQuerySpec("range", "Name", "A", "M");
        ReadableMap args = createMockReadableMap(
            "soupName", "accounts",
            "querySpec", querySpec
        );
        
        StoreCursor mockCursor = mock(StoreCursor.class);
        when(mockCursor.getDataSerialized(mockStore)).thenReturn(
            new JSONObject()
                .put("cursorId", 1)
                .put("totalEntries", 10)
                .put("currentPageIndex", 0)
                .put("totalPages", 1)
        );
        
        Callback successCallback = mock(Callback.class);
        Callback errorCallback = mock(Callback.class);
        
        // When: querySoup called
        bridge.querySoup(args, successCallback, errorCallback);
        
        // Then: Success callback invoked with cursor JSON
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(successCallback).invoke(captor.capture());
        
        String result = captor.getValue();
        JSONObject cursor = new JSONObject(result);
        assertEquals(1, cursor.getInt("cursorId"));
        assertEquals(10, cursor.getInt("totalEntries"));
    }
}
```

## Testing Authentication Flow

Authentication involves complex interactions between activity lifecycle, OAuth flow, and callback coordination.

### Test Scenarios

1. **Already authenticated**: Client exists, callbacks invoked immediately
2. **OAuth required**: Activity pauses for login, resumes with client
3. **Offline with shouldAuthenticate=true**: Error handler called
4. **Token refresh**: Expired token triggers automatic refresh
5. **Logout**: Clears session and local data

### Example: Authentication Flow Test

```java
@RunWith(AndroidJUnit4.class)
public class SalesforceReactActivityAuthTest {
    
    @Rule
    public ActivityScenarioRule<TestReactActivity> activityRule =
        new ActivityScenarioRule<>(TestReactActivity.class);
    
    private ClientManager mockClientManager;
    private RestClient mockRestClient;
    
    @Before
    public void setUp() {
        mockClientManager = mock(ClientManager.class);
        mockRestClient = mock(RestClient.class);
        
        // Inject mocks
        activityRule.getScenario().onActivity(activity -> {
            activity.setClientManager(mockClientManager);
        });
    }
    
    @Test
    public void test_givenAlreadyAuthenticated_whenAuthenticate_thenImmediateCallback() {
        // Given: Already authenticated
        when(mockClientManager.peekRestClient()).thenReturn(mockRestClient);
        
        activityRule.getScenario().onActivity(activity -> {
            AtomicBoolean successCalled = new AtomicBoolean(false);
            Callback successCallback = (args) -> successCalled.set(true);
            Callback errorCallback = (args) -> fail("Should not call error callback");
            
            // When: authenticate called
            activity.authenticate(successCallback, errorCallback);
            
            // Then: Success callback invoked immediately (same thread)
            assertTrue(successCalled.get());
        });
    }
    
    @Test
    public void test_givenOAuthRequired_whenAuthenticate_thenCallbackAfterResume() {
        // Given: No cached client (OAuth required)
        when(mockClientManager.peekRestClient()).thenReturn(null);
        
        // Simulate OAuth flow
        doAnswer(invocation -> {
            RestClientCallback callback = invocation.getArgument(1);
            // Simulate async OAuth completion
            new Handler(Looper.getMainLooper()).post(() -> {
                callback.authenticatedRestClient(mockRestClient);
            });
            return null;
        }).when(mockClientManager).getRestClient(any(), any());
        
        activityRule.getScenario().onActivity(activity -> {
            AtomicBoolean successCalled = new AtomicBoolean(false);
            Callback successCallback = (args) -> successCalled.set(true);
            Callback errorCallback = (args) -> fail("Should not call error callback");
            
            // When: authenticate called
            activity.authenticate(successCallback, errorCallback);
            
            // Then: Success callback eventually invoked
            // (Would need to wait for async completion in real test)
        });
    }
    
    @Test
    public void test_givenShouldAuthenticateTrue_whenOffline_thenErrorHandlerCalled() {
        // Given: Should authenticate but offline
        activityRule.getScenario().onActivity(activity -> {
            activity.setShouldAuthenticate(true);
            
            // Mock network as offline
            SalesforceReactSDKManager mockManager = mock(SalesforceReactSDKManager.class);
            when(mockManager.hasNetwork()).thenReturn(false);
            
            // When: onResume called (not authenticated)
            activity.client = null;
            
            // Trigger onResumeNotLoggedIn flow
            activity.onResume();
            
            // Then: onErrorAuthenticateOffline should be called
            // (Verify via spy or custom test activity)
        });
    }
}
```

### Testing Pending Callback Coordination

```java
@Test
public void test_givenPendingCallbacks_whenBothCallbackAndResumeRun_thenInvokedOnce() {
    // This tests the race condition handling between 
    // authenticatedRestClient callback and onResume
    
    activityRule.getScenario().onActivity(activity -> {
        AtomicInteger callCount = new AtomicInteger(0);
        Callback successCallback = (args) -> callCount.incrementAndGet();
        Callback errorCallback = (args) -> fail("Should not error");
        
        // Set pending callbacks
        activity.pendingAuthSuccessCallback = successCallback;
        activity.pendingAuthErrorCallback = errorCallback;
        
        // Simulate both paths running (race condition)
        activity.onResume(mockRestClient);  // First path clears callbacks
        activity.getAuthCredentials(
            activity.pendingAuthSuccessCallback,  // Now null
            activity.pendingAuthErrorCallback     // Now null
        );
        
        // Then: Callback invoked exactly once
        assertEquals(1, callCount.get());
    });
}
```

## Testing SmartStore Integration

SmartStore tests focus on:
- Data persistence
- Query correctness
- Transaction integrity
- Cursor pagination
- Index management

### Example: SmartStore Integration Test

```java
@RunWith(AndroidJUnit4.class)
public class SmartStoreIntegrationTest {
    
    private SmartStore store;
    private static final String TEST_SOUP = "test_accounts";
    
    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UserAccount testAccount = createTestUserAccount();
        store = SmartStoreSDKManager.getInstance()
            .getSmartStore("test_store", testAccount, null);
        
        // Register test soup
        IndexSpec[] indexes = {
            new IndexSpec("Id", SmartStore.Type.string),
            new IndexSpec("Name", SmartStore.Type.string),
            new IndexSpec("Industry", SmartStore.Type.string)
        };
        store.registerSoup(TEST_SOUP, indexes);
    }
    
    @After
    public void tearDown() {
        if (store.hasSoup(TEST_SOUP)) {
            store.dropSoup(TEST_SOUP);
        }
    }
    
    @Test
    public void test_givenEntries_whenUpsertAndQuery_thenDataPersisted() throws JSONException {
        // Given: Test data
        JSONObject entry1 = new JSONObject()
            .put("Id", "001")
            .put("Name", "Acme Corp")
            .put("Industry", "Technology");
        
        JSONObject entry2 = new JSONObject()
            .put("Id", "002")
            .put("Name", "Beta Inc")
            .put("Industry", "Finance");
        
        // When: Upsert entries
        store.upsert(TEST_SOUP, entry1, "Id");
        store.upsert(TEST_SOUP, entry2, "Id");
        
        // Then: Query returns both entries
        QuerySpec querySpec = QuerySpec.buildAllQuerySpec(TEST_SOUP, "Name", Order.ascending, 10);
        JSONArray results = store.query(querySpec, 0);
        
        assertEquals(2, results.length());
        assertEquals("Acme Corp", results.getJSONObject(0).getString("Name"));
        assertEquals("Beta Inc", results.getJSONObject(1).getString("Name"));
    }
    
    @Test
    public void test_givenRangeQuery_whenQuerySoup_thenCorrectSubset() throws JSONException {
        // Given: Multiple entries
        insertTestData(store, TEST_SOUP, 
            entry("001", "Alpha", "Tech"),
            entry("002", "Beta", "Finance"),
            entry("003", "Gamma", "Tech"),
            entry("004", "Delta", "Retail")
        );
        
        // When: Range query on Name
        QuerySpec querySpec = QuerySpec.buildRangeQuerySpec(
            TEST_SOUP, "Name", "Beta", "Gamma", Order.ascending, 10
        );
        JSONArray results = store.query(querySpec, 0);
        
        // Then: Only entries in range returned
        assertEquals(2, results.length());
        assertEquals("Beta", results.getJSONObject(0).getString("Name"));
        assertEquals("Gamma", results.getJSONObject(1).getString("Name"));
    }
    
    @Test
    public void test_givenSmartSQL_whenQueryMultipleSoups_thenJoinWorks() throws JSONException {
        // Setup additional soup for join
        String CONTACT_SOUP = "test_contacts";
        store.registerSoup(CONTACT_SOUP, new IndexSpec[] {
            new IndexSpec("Id", SmartStore.Type.string),
            new IndexSpec("AccountId", SmartStore.Type.string),
            new IndexSpec("Name", SmartStore.Type.string)
        });
        
        // Given: Related data in two soups
        store.upsert(TEST_SOUP, entry("001", "Acme", "Tech"), "Id");
        store.upsert(CONTACT_SOUP, 
            new JSONObject()
                .put("Id", "003")
                .put("AccountId", "001")
                .put("Name", "John Doe"),
            "Id"
        );
        
        // When: Smart SQL join
        String smartSql = "SELECT {test_accounts:Name}, {test_contacts:Name} " +
                         "FROM {test_accounts} " +
                         "INNER JOIN {test_contacts} ON {test_accounts:Id} = {test_contacts:AccountId}";
        QuerySpec querySpec = QuerySpec.buildSmartQuerySpec(smartSql, 10);
        JSONArray results = store.query(querySpec, 0);
        
        // Then: Joined results returned
        assertEquals(1, results.length());
        JSONArray row = results.getJSONArray(0);
        assertEquals("Acme", row.getString(0));
        assertEquals("John Doe", row.getString(1));
        
        // Cleanup
        store.dropSoup(CONTACT_SOUP);
    }
    
    private JSONObject entry(String id, String name, String industry) throws JSONException {
        return new JSONObject()
            .put("Id", id)
            .put("Name", name)
            .put("Industry", industry);
    }
}
```

## Testing MobileSync

MobileSync tests verify sync behavior:
- Sync down from server to local
- Sync up from local to server
- Conflict resolution
- Ghost record cleanup

### Example: MobileSync Test

```java
@RunWith(AndroidJUnit4.class)
public class MobileSyncIntegrationTest {
    
    private SyncManager syncManager;
    private SmartStore store;
    private RestClient restClient;
    private static final String TEST_SOUP = "test_sync_accounts";
    
    @Before
    public void setUp() throws Exception {
        // Setup authenticated client
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UserAccount testAccount = createTestUserAccount();
        store = SmartStoreSDKManager.getInstance()
            .getSmartStore("test_store", testAccount, null);
        restClient = getAuthenticatedRestClient(); // Helper to get real/mock client
        
        syncManager = SyncManager.getInstance(testAccount, null, store);
        
        // Register soup for sync
        store.registerSoup(TEST_SOUP, new IndexSpec[] {
            new IndexSpec("Id", SmartStore.Type.string),
            new IndexSpec("Name", SmartStore.Type.string),
            new IndexSpec(SyncManager.LOCALLY_CREATED, SmartStore.Type.string),
            new IndexSpec(SyncManager.LOCALLY_UPDATED, SmartStore.Type.string),
            new IndexSpec(SyncManager.LOCALLY_DELETED, SmartStore.Type.string),
            new IndexSpec(SyncManager.LOCAL, SmartStore.Type.string)
        });
    }
    
    @After
    public void tearDown() {
        if (store.hasSoup(TEST_SOUP)) {
            store.dropSoup(TEST_SOUP);
        }
    }
    
    @Test
    public void test_givenSOQLTarget_whenSyncDown_thenRecordsDownloaded() throws Exception {
        // Given: SOQL sync down target
        SyncDownTarget target = new SoqlSyncDownTarget(
            "SELECT Id, Name, Industry FROM Account WHERE Industry = 'Technology' LIMIT 10"
        );
        SyncOptions options = SyncOptions.optionsForSyncDown(SyncState.MergeMode.OVERWRITE);
        
        // When: Sync down
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<SyncState> finalState = new AtomicReference<>();
        
        syncManager.syncDown(target, options, TEST_SOUP, new SyncManager.SyncUpdateCallback() {
            @Override
            public void onUpdate(SyncState sync) {
                if (sync.isDone() || sync.hasFailed()) {
                    finalState.set(sync);
                    latch.countDown();
                }
            }
        });
        
        latch.await(30, TimeUnit.SECONDS);
        
        // Then: Records downloaded to soup
        SyncState result = finalState.get();
        assertNotNull(result);
        assertEquals(SyncState.Status.DONE, result.getStatus());
        assertTrue(result.getTotalSize() > 0);
        
        // Verify data in soup
        QuerySpec querySpec = QuerySpec.buildAllQuerySpec(TEST_SOUP, "Name", Order.ascending, 100);
        JSONArray records = store.query(querySpec, 0);
        assertEquals(result.getTotalSize(), records.length());
    }
    
    @Test
    public void test_givenLocalChanges_whenSyncUp_thenChangesSyncedToServer() throws Exception {
        // Given: Local changes in soup
        JSONObject localAccount = new JSONObject()
            .put("Id", null) // No server ID yet
            .put("Name", "Test Account Created Locally")
            .put("Industry", "Technology")
            .put(SyncManager.LOCAL, true)
            .put(SyncManager.LOCALLY_CREATED, true)
            .put(SyncManager.LOCALLY_UPDATED, false)
            .put(SyncManager.LOCALLY_DELETED, false);
        
        store.upsert(TEST_SOUP, localAccount);
        
        // When: Sync up
        SyncUpTarget target = new SyncUpTarget();
        SyncOptions options = SyncOptions.optionsForSyncUp(
            Arrays.asList("Name", "Industry"),
            SyncState.MergeMode.OVERWRITE
        );
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<SyncState> finalState = new AtomicReference<>();
        
        syncManager.syncUp(target, options, TEST_SOUP, new SyncManager.SyncUpdateCallback() {
            @Override
            public void onUpdate(SyncState sync) {
                if (sync.isDone() || sync.hasFailed()) {
                    finalState.set(sync);
                    latch.countDown();
                }
            }
        });
        
        latch.await(30, TimeUnit.SECONDS);
        
        // Then: Sync completed
        SyncState result = finalState.get();
        assertNotNull(result);
        assertEquals(SyncState.Status.DONE, result.getStatus());
        
        // Verify record now has server ID
        QuerySpec querySpec = QuerySpec.buildAllQuerySpec(TEST_SOUP, null, null, 100);
        JSONArray records = store.query(querySpec, 0);
        JSONObject syncedRecord = records.getJSONObject(0);
        assertNotNull(syncedRecord.getString("Id"));
        assertTrue(syncedRecord.getString("Id").startsWith("001")); // Salesforce ID format
        assertFalse(syncedRecord.getBoolean(SyncManager.LOCAL));
        assertFalse(syncedRecord.getBoolean(SyncManager.LOCALLY_CREATED));
    }
    
    @Test
    public void test_givenDeletedServerRecords_whenCleanGhosts_thenRemovedFromSoup() throws Exception {
        // Given: Records synced down, then deleted on server
        // (Would need to setup by syncing down, then deleting on server, then syncing down again)
        
        // Setup initial sync
        SyncDownTarget target = new SoqlSyncDownTarget("SELECT Id, Name FROM Account LIMIT 5");
        SyncOptions options = SyncOptions.optionsForSyncDown(SyncState.MergeMode.OVERWRITE);
        
        CountDownLatch latch = new CountDownLatch(1);
        final long[] syncId = new long[1];
        
        syncManager.syncDown(target, options, TEST_SOUP, new SyncManager.SyncUpdateCallback() {
            @Override
            public void onUpdate(SyncState sync) {
                if (sync.isDone()) {
                    syncId[0] = sync.getId();
                    latch.countDown();
                }
            }
        });
        latch.await(30, TimeUnit.SECONDS);
        
        int initialCount = store.query(
            QuerySpec.buildAllQuerySpec(TEST_SOUP, null, null, 100), 0
        ).length();
        
        // Simulate server deletion by removing IDs from SOQL result
        // (In real test, would delete records on server, then re-sync)
        
        // When: Clean ghosts
        CountDownLatch ghostLatch = new CountDownLatch(1);
        AtomicInteger removedCount = new AtomicInteger(0);
        
        syncManager.cleanResyncGhosts(syncId[0], new SyncManager.CleanResyncGhostsCallback() {
            @Override
            public void onSuccess(int numRecords) {
                removedCount.set(numRecords);
                ghostLatch.countDown();
            }
            
            @Override
            public void onError(Exception e) {
                fail("Clean ghosts failed: " + e.getMessage());
            }
        });
        
        ghostLatch.await(30, TimeUnit.SECONDS);
        
        // Then: Ghost records removed
        int finalCount = store.query(
            QuerySpec.buildAllQuerySpec(TEST_SOUP, null, null, 100), 0
        ).length();
        
        assertEquals(initialCount - removedCount.get(), finalCount);
    }
}
```

## Testing Activity Lifecycle

Activity lifecycle tests verify correct behavior across:
- Configuration changes (rotation)
- Process death and recreation
- Background/foreground transitions

### Example: Lifecycle Test

```java
@RunWith(AndroidJUnit4.class)
public class SalesforceReactActivityLifecycleTest {
    
    @Rule
    public ActivityScenarioRule<TestReactActivity> activityRule =
        new ActivityScenarioRule<>(TestReactActivity.class);
    
    @Test
    public void test_givenAuthenticated_whenRotation_thenStatePreserved() {
        // Given: Authenticated activity
        activityRule.getScenario().onActivity(activity -> {
            activity.setRestClient(createMockRestClient());
        });
        
        // When: Rotate device
        activityRule.getScenario().recreate();
        
        // Then: Client still available
        activityRule.getScenario().onActivity(activity -> {
            assertNotNull(activity.getRestClient());
        });
    }
    
    @Test
    public void test_givenReactLoaded_whenBackground_thenReactPaused() {
        // Track lifecycle calls
        AtomicBoolean pauseCalled = new AtomicBoolean(false);
        
        activityRule.getScenario().onActivity(activity -> {
            // Mock that React is loaded
            activity.reactActivityDelegate.loaded = true;
        });
        
        // When: Move to background
        activityRule.getScenario().moveToState(Lifecycle.State.STARTED);
        
        // Then: onPause called
        activityRule.getScenario().onActivity(activity -> {
            // Verify pause was called (would need spy or test activity)
            assertTrue(true); // Placeholder
        });
    }
}
```

## Integration Testing

Integration tests use real components where possible:
- Real SmartStore with test database
- Real REST calls to sandbox org
- Real OAuth flow (with test credentials)

### Test Credentials Setup

```java
// Test credentials should be in test_credentials.json
public class TestCredentials {
    
    private static JSONObject credentials;
    
    static {
        try {
            Context context = InstrumentationRegistry.getInstrumentation().getContext();
            InputStream is = context.getAssets().open("test_credentials.json");
            String json = readStream(is);
            credentials = new JSONObject(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load test credentials", e);
        }
    }
    
    public static String getLoginUrl() {
        return credentials.optString("loginUrl", "https://test.salesforce.com");
    }
    
    public static String getClientId() {
        return credentials.getString("clientId");
    }
    
    public static String getRedirectUri() {
        return credentials.getString("redirectUri");
    }
    
    public static String getUsername() {
        return credentials.getString("username");
    }
    
    public static String getPassword() {
        return credentials.getString("password");
    }
}
```

## Common Test Patterns

### Pattern: Mock ReadableMap

```java
public static ReadableMap createMockReadableMap(Object... keyValuePairs) {
    ReadableMap map = mock(ReadableMap.class);
    Map<String, Object> data = new HashMap<>();
    
    for (int i = 0; i < keyValuePairs.length; i += 2) {
        String key = (String) keyValuePairs[i];
        Object value = keyValuePairs[i + 1];
        data.put(key, value);
        
        when(map.hasKey(key)).thenReturn(true);
        when(map.isNull(key)).thenReturn(value == null);
        
        if (value instanceof String) {
            when(map.getString(key)).thenReturn((String) value);
        } else if (value instanceof Boolean) {
            when(map.getBoolean(key)).thenReturn((Boolean) value);
        } else if (value instanceof Integer) {
            when(map.getInt(key)).thenReturn((Integer) value);
        } else if (value instanceof ReadableMap) {
            when(map.getMap(key)).thenReturn((ReadableMap) value);
        } else if (value instanceof ReadableArray) {
            when(map.getArray(key)).thenReturn((ReadableArray) value);
        }
    }
    
    return map;
}
```

### Pattern: Async Callback Testing

```java
@Test
public void test_givenAsyncOperation_whenComplete_thenCallbackInvoked() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> result = new AtomicReference<>();
    
    Callback successCallback = (args) -> {
        result.set(args[0].toString());
        latch.countDown();
    };
    
    Callback errorCallback = (args) -> {
        fail("Should not call error: " + args[0]);
    };
    
    // Trigger async operation
    bridge.someAsyncMethod(args, successCallback, errorCallback);
    
    // Wait for completion (with timeout)
    assertTrue("Timeout waiting for callback", latch.await(10, TimeUnit.SECONDS));
    assertNotNull(result.get());
}
```

### Pattern: Verify Callback Never Called

```java
@Test
public void test_givenError_whenOperation_thenSuccessNotCalled() {
    Callback successCallback = mock(Callback.class);
    Callback errorCallback = mock(Callback.class);
    
    // Trigger operation that should fail
    bridge.failingMethod(args, successCallback, errorCallback);
    
    // Verify
    verify(successCallback, never()).invoke(any());
    verify(errorCallback).invoke(any());
}
```

## Test Utilities

### Helper: Create Test User Account

```java
public static UserAccount createTestUserAccount() {
    return new UserAccount(
        "test-user-id",
        "test-org-id",
        "https://test.salesforce.com",
        "https://test.salesforce.com/id/test-org/test-user",
        "test@example.com",
        "Test",
        "User",
        "Test User",
        null, // photoUrl
        null, // thumbnailUrl
        null  // communityId
    );
}
```

### Helper: Create Mock RestClient

```java
public static RestClient createMockRestClient() {
    ClientManager.LoginOptions loginOptions = new ClientManager.LoginOptions(
        "https://test.salesforce.com",
        "test-client-id",
        "testapp://callback",
        new String[] {"api"}
    );
    
    ClientManager.AccMgrAuthTokenProvider tokenProvider = mock(ClientManager.AccMgrAuthTokenProvider.class);
    when(tokenProvider.getAuthToken()).thenReturn("mock-access-token");
    when(tokenProvider.getRefreshToken()).thenReturn("mock-refresh-token");
    when(tokenProvider.getInstanceUrl()).thenReturn("https://test.salesforce.com");
    
    return new RestClient(
        null, // clientInfo
        "mock-access-token",
        "https://test.salesforce.com",
        loginOptions,
        tokenProvider
    );
}
```

## Best Practices

1. **Isolate tests**: Each test should be independent and not rely on other tests
2. **Clean up**: Always clean up test data (soups, syncs, stores) in @After methods
3. **Use test databases**: Never run tests against production SmartStore
4. **Mock external dependencies**: Mock network calls and OAuth flow where appropriate
5. **Test error paths**: Don't just test happy path - test failure scenarios
6. **Use descriptive names**: Follow `test_given[Condition]_when[Action]_then[Expected]` naming
7. **Avoid sleeps**: Use CountDownLatch or proper async test utilities instead of Thread.sleep()
8. **Test thread safety**: Verify concurrent access to SmartStore and callbacks
9. **Test memory leaks**: Ensure cursors are closed, callbacks are cleared
10. **Document complex tests**: Add comments explaining non-obvious test logic

## Running Tests

### Command Line

```bash
# Run all instrumented tests
./gradlew :libs:SalesforceReact:connectedAndroidTest

# Run specific test class
./gradlew :libs:SalesforceReact:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=\
com.salesforce.androidsdk.reactnative.bridge.SmartStoreReactBridgeTest

# Run with coverage
./gradlew :libs:SalesforceReact:connectedAndroidTest \
  -Pandroid.testInstrumentationRunner=androidx.test.runner.AndroidJUnitRunner \
  -Pandroid.testInstrumentationRunnerArguments.coverage=true
```

### Android Studio

1. Right-click test class or method
2. Select "Run 'TestName'"
3. View results in Run window

## Continuous Integration

Tests should run in CI on:
- Pull request creation
- Merge to main branch
- Nightly builds

### Firebase Test Lab

```bash
# Upload APKs to Firebase Test Lab
gcloud firebase test android run \
  --type instrumentation \
  --app app/build/outputs/apk/debug/app-debug.apk \
  --test libs/SalesforceReact/build/outputs/apk/androidTest/debug/SalesforceReact-debug-androidTest.apk \
  --device model=Pixel2,version=28,locale=en,orientation=portrait
```

## Debugging Tests

### Enable Verbose Logging

```java
@Before
public void setUp() {
    SalesforceReactLogger.setLogLevel(SalesforceLogger.Level.VERBOSE);
}
```

### View Logcat

```bash
adb logcat -s SalesforceReact:V
```

### Inspect SmartStore Database

```bash
# Pull database from device
adb pull /data/data/com.yourapp/databases/smartstore.db

# Open with SQLite browser
sqlite3 smartstore.db
```

## Summary

Testing the SalesforceReact library requires:

- **Unit tests** for bridge modules, helpers, and SDK manager
- **Integration tests** for SmartStore, MobileSync, and authentication flow
- **Lifecycle tests** for activity state management
- **End-to-end tests** with real Salesforce sandbox org

The key challenges are:
- Async callback coordination
- Activity lifecycle complexity
- React Native bridge communication
- Multi-layer architecture (JS → Bridge → Activity → SDK)

By following the patterns and practices in this guide, you can build a comprehensive test suite that ensures the SalesforceReact library works reliably across all scenarios.

## See Also

- [README.md](README.md) - Library overview
- [ARCHITECTURE.md](ARCHITECTURE.md) - Technical architecture
- [API_REFERENCE.md](API_REFERENCE.md) - API documentation
- [Android Testing Guide](https://developer.android.com/training/testing)
- [Salesforce Mobile SDK Testing](https://developer.salesforce.com/docs/platform/mobile-sdk/guide)
