/*
 * Copyright (c) 2016-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.reactnative.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.widget.Toast;

import com.facebook.react.ReactActivity;
import com.facebook.react.ReactActivityDelegate;
import com.facebook.react.bridge.Callback;
import com.salesforce.androidsdk.reactnative.R;
import com.salesforce.androidsdk.reactnative.app.SalesforceReactSDKManager;
import com.salesforce.androidsdk.reactnative.bridge.ReactBridgeHelper;
import com.salesforce.androidsdk.reactnative.util.SalesforceReactLogger;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.ui.SalesforceActivityDelegate;
import com.salesforce.androidsdk.ui.SalesforceActivityInterface;

/**
 * Main activity for a Salesforce ReactNative app.
 */
public abstract class SalesforceReactActivity extends ReactActivity implements SalesforceActivityInterface {

    private static final String TAG = "SFReactActivity";

    private final SalesforceActivityDelegate delegate;
    private RestClient client;
    private ClientManager clientManager;
    private SalesforceReactActivityDelegate reactActivityDelegate;
    AlertDialog overlayPermissionRequiredDialog;

    /**
     * Pending callback for authentication requests from the React Native bridge.
     *
     * When authenticate() is called from JavaScript:
     * - This callback is stored in a pending variable
     * - It is invoked once authentication completes (either immediately if already
     *   authenticated, or after OAuth flow completes)
     * - Two code paths can invoke it: authenticatedRestClient() callback (always runs)
     *   or onResume() (only runs after OAuth pause/resume cycle)
     * - Whichever path runs first invokes the callback and clears it to null
     * - The other path sees null and does nothing, preventing double invocation
     *
     * Uses single-callback pattern: callback(null, result) for success, callback(error) for error.
     *
     * See authenticate() and onResume(RestClient) for the coordination logic.
     */
    private Callback pendingAuthCallback;

    protected SalesforceReactActivity() {
        super();
        delegate = new SalesforceActivityDelegate(this);
    }

    /**
     * Returns if authentication should be performed automatically or not.
     *
     * @return True - if you want login to happen as soon as activity is loaded, False - otherwise.
     */
    public boolean shouldAuthenticate() {
        return true;
    }

    /**
     * Called if shouldAuthenticate() returned true but device is offline.
     */
    public void onErrorAuthenticateOffline() {
        final Toast t = Toast.makeText(this,
                R.string.sf__should_authenticate_but_is_offline, Toast.LENGTH_LONG);
        t.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SalesforceReactLogger.i(TAG, "onCreate called");
        super.onCreate(savedInstanceState);
        clientManager = buildClientManager();
        delegate.onCreate();
    }

    @Override
    public void onResume() {
        super.onResume();
        delegate.onResume(false);
        loadReactAppOnceIfReady();
    }

    @Override
    public void onResume(RestClient c) {
        try {
            setRestClient(clientManager.peekRestClient());
        } catch (ClientManager.AccountInfoNotFoundException e) {
            setRestClient(client);
        }

        // Not logged in.
        if (client == null) {
            onResumeNotLoggedIn();
        }

        // Logged in.
        else {
            SalesforceReactLogger.i(TAG, "onResume - already logged in");

            // If we have a pending auth callback (from deferred authentication via authenticate()),
            // invoke it now. This handles the OAuth flow scenario where the activity was paused
            // for login and is now resuming.
            //
            // NOTE: This works in coordination with authenticate()'s authenticatedRestClient callback.
            // In the OAuth flow, there's a race condition between onResume() and authenticatedRestClient().
            // Whichever runs first will find pending callback non-null, invoke it, and set it to null.
            // The other will find it null and do nothing. This ensures the callback is invoked exactly once.
            //
            // For the "already authenticated" scenario, authenticatedRestClient() invokes the callback
            // immediately without any pause/resume cycle, so this code is never reached.
            if (pendingAuthCallback != null) {
                SalesforceReactLogger.i(TAG, "onResume - invoking pending auth callback");
                getAuthCredentials(pendingAuthCallback);
                pendingAuthCallback = null;
            }
        }
    }

    private void onResumeNotLoggedIn() {

        // Need to be authenticated.
        if (shouldAuthenticate()) {

            // Online.
            if (SalesforceReactSDKManager.getInstance().hasNetwork()) {
                SalesforceReactLogger.i(TAG, "onResumeNotLoggedIn - should authenticate/online - authenticating");
                login();
            }

            // Offline.
            else {
                SalesforceReactLogger.w(TAG, "onResumeNotLoggedIn - should authenticate/offline - can not proceed");
                onErrorAuthenticateOffline();
            }
        }

        // Does not need to be authenticated.
        else {
            SalesforceReactLogger.i(TAG, "onResumeNotLoggedIn - should not authenticate");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        delegate.onPause();
    }

    @Override
    public void onDestroy() {
        delegate.onDestroy();
        super.onDestroy();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return delegate.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event);
    }

    public void showReactDevOptionsDialog() {
        getReactNativeHost().getReactInstanceManager().showDevOptionsDialog();
    }

    protected void login() {
        SalesforceReactLogger.i(TAG, "login called");
        clientManager.getRestClient(this, new RestClientCallback() {

            @Override
            public void authenticatedRestClient(RestClient client) {
                if (client == null) {
                    SalesforceReactLogger.i(TAG, "login callback triggered with null client");
                    logout(null);
                } else {
                    SalesforceReactLogger.i(TAG, "login callback triggered with actual client");
                    SalesforceReactActivity.this.restartReactNativeApp();
                }
            }
        });
    }

    /**
     * Method called from bridge to logout.
     *
     * @param callback Single callback: callback(null, result) on success.
     */
    public void logout(final Callback callback) {
        SalesforceReactLogger.i(TAG, "logout called");
        SalesforceReactSDKManager.getInstance().logout(this);
        if (callback != null) {
            ReactBridgeHelper.invokeSuccess(callback, "Logout complete");
        }
    }

    /**
     * Method called from bridge to authenticate.
     *
     * @param callback Single callback: callback(null, result) on success, callback(error) on error.
     */
    public void authenticate(final Callback callback) {
        SalesforceReactLogger.i(TAG, "authenticate called");

        // Store callback in pending variable to handle both authentication scenarios:
        //
        // SCENARIO 1: Already authenticated (no OAuth needed)
        //   - getRestClient() callback is invoked immediately on the same thread
        //   - authenticatedRestClient() below invokes callback immediately
        //   - Activity does NOT pause/resume, so onResume() is NOT called again
        //   - Callback is successfully invoked ✓
        //
        // SCENARIO 2: OAuth required (activity will pause/resume)
        //   - getRestClient() starts OAuth flow
        //   - Activity pauses (goes to login screen)
        //   - User completes OAuth, activity resumes
        //   - Either authenticatedRestClient() or onResume() runs first (race condition)
        //   - Whichever runs first invokes callback and clears pending variable
        //   - The other sees null pending variable and does nothing
        //   - Callback is successfully invoked exactly once ✓
        pendingAuthCallback = callback;

        clientManager.getRestClient(this, new RestClientCallback() {

            @Override
            public void authenticatedRestClient(RestClient client) {
                SalesforceReactLogger.i(TAG, "authenticatedRestClient callback invoked");
                SalesforceReactActivity.this.setRestClient(client);

                if (pendingAuthCallback != null) {
                    SalesforceReactLogger.i(TAG, "authenticatedRestClient - invoking pending callback");
                    getAuthCredentials(pendingAuthCallback);
                    pendingAuthCallback = null;
                }
            }
        });
    }

    /**
     * Method called from bridge to get auth credentials.
     *
     * @param callback Single callback: callback(null, result) on success, callback(error) on error.
     */
    public void getAuthCredentials(Callback callback) {
        SalesforceReactLogger.i(TAG, "getAuthCredentials called");
        if (client != null) {
            if (callback != null) {
                ReactBridgeHelper.invokeSuccess(callback, client.getJSONCredentials());
            }
        } else {
            if (callback != null) {
                ReactBridgeHelper.invokeError(callback, "Not authenticated");
            }
        }
    }

    /**
     * Returns an instance of RestClient.
     *
     * @return An instance of RestClient.
     */
    public RestClient getRestClient() {
        return client;
    }

    protected void setRestClient(RestClient restClient) {
        client = restClient;
        if (client != null) {
            loadReactAppOnceIfReady();
        }
    }

    public ClientManager buildClientManager() {
        return SalesforceReactSDKManager.getInstance().getClientManager();
    }

    @Override
    public void onLogoutComplete() {
    }

    @Override
    public void onUserSwitched() {
    }

    @Override
    protected ReactActivityDelegate createReactActivityDelegate() {
        reactActivityDelegate = new SalesforceReactActivityDelegate(this, getMainComponentName());
        return reactActivityDelegate;
    }

    protected boolean shouldReactBeRunning() {
        return !shouldAskOverlayPermission() && (!shouldAuthenticate() || client != null);
    }

    protected void restartReactNativeApp() {
        SalesforceReactActivity.this.getReactNativeHost().getReactInstanceManager().destroy();
        if (shouldReactBeRunning()) {
            SalesforceReactActivity.this.getReactNativeHost().getReactInstanceManager().createReactContextInBackground();
        }
    }

    private boolean shouldAskOverlayPermission() {
        if (SalesforceReactActivity.this.getReactNativeHost().getReactInstanceManager().getDevSupportManager().getDevSupportEnabled()) {
            if (!Settings.canDrawOverlays(this)) {
                showPermissionWarning();
                return true;
            } else {
                hidePermissionWarning();
            }
        }
        return false;
    }

    private void loadReactAppOnceIfReady() {
        if (reactActivityDelegate != null ) {
            reactActivityDelegate.loadReactAppOnceIfReady(getMainComponentName());
        }
    }

    private void showPermissionWarning() {
        if (overlayPermissionRequiredDialog == null) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Developer mode: Overlay permissions need to be granted");
            builder.setCancelable(false);
            builder.setPositiveButton("Continue", new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                    SalesforceReactActivity.this.recreate();
                }
            });
            overlayPermissionRequiredDialog = builder.create();
        }
        if (!overlayPermissionRequiredDialog.isShowing()) {
            overlayPermissionRequiredDialog.show();
        }
    }

    private void hidePermissionWarning() {
        if (overlayPermissionRequiredDialog != null) {
            overlayPermissionRequiredDialog.dismiss();
        }
    }
}
