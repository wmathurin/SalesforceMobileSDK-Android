/*
 * Copyright (c) 2014, salesforce.com, inc.
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
package com.salesforce.androidsdk.tracking;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.app.SalesforceSDKAppInfo;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.security.Encryptor;

/**
 * UsageTracker that simply write events / screen views to the log
 */
public class UsageTracker {
	
	// Misc constant
	private static final long MILLIS_PER_DAY = 1000 * 3600 * 24;
	
	// Fields
	private SalesforceSDKManager sdkMgr;
	
    /**
	 * @return new UsageTracker
	 */
	public static UsageTracker newInstance(Context context) {
		UsageTracker tracker;

		String trackerClass = context.getResources().getString(R.string.usage_tracker_class);
		try {
			tracker = (UsageTracker) Class.forName(trackerClass).newInstance();
			tracker.init(context);
		} catch (Exception e) {
			Log.w("UsageTracker", "Could not create and initialized usage tracker of type " + trackerClass, e);
			Log.i("UsageTracker", "Using local logging only");
			tracker = new UsageTracker();
			tracker.init(context);
		}
		Log.i("UsageTracker", "Using usage tracker of type " + tracker.getClass().getName());
		
		return tracker;
	}

	/**
	 * @return UsageTracker setup for this application
	 */
	public static UsageTracker getInstance() {
		return SalesforceSDKManager.getInstance().getUsageTracker();
	}
	
	/**
	 * Protected constructor
	 */
	protected UsageTracker() {
    	sdkMgr = SalesforceSDKManager.getInstance();
	}
	
	/**
     * Initialization
     * @param context
     */
    protected void init(Context context) {
    }
    
    /**
     * Report event
     */
    public void reportEvent(String category, String action, String label) {
		Log.i(getClass().getSimpleName(), "Recording event: " + category + "|" + action + "|" + label);
    }
    
    /**
     * Report screen view
     * @param params
     */
    public void reportScreenView(Activity activity) {
		String screenName = activity.getClass().getSimpleName();
		Log.i(getClass().getSimpleName(), "Recording screen view: " + screenName);
    }
    
    /**
     * Report end of screen view
     * @param activity
     */
    public void reportScreenViewEnd(Activity activity) {
		String screenName = activity.getClass().getSimpleName();
		Log.i(getClass().getSimpleName(), "Recording screen view end: " + screenName);
    }
    
    /**
     * @return hashed org id
     */
    protected String getHashedOrgId() {
    	UserAccount userAccount = SalesforceSDKManager.getInstance().getUserAccountManager().getCurrentUser();
    	return (userAccount != null ? Encryptor.hash(userAccount.getOrgId(), userAccount.getOrgId()) : "NotLoggedIn");
    }
    
    /**
     * @return app age i.e. number of days since app was first installed
     */
    protected long getAppAge() {
    	return (getAppInfo().appInstallTime - System.currentTimeMillis()) / MILLIS_PER_DAY;
    }
    
    /**
     * @return app info
     */
    protected SalesforceSDKAppInfo getAppInfo() {
    	return sdkMgr.getAppInfo();
    }
}