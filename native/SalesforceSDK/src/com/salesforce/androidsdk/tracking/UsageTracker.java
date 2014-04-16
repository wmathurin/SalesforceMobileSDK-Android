/*
 * Copyright (c) 2012, salesforce.com, inc.
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

import android.content.Context;
import android.util.Log;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.BootConfig;

public class UsageTracker {

	private static UsageTracker INSTANCE;
	private Tracker tracker;
	private String trackerScreenPrefix;
	
	/**
     * Returns a singleton instance of this class.
     *
     * @param context Application context.
     * @return Singleton instance of SalesforceSDKManager.
     */
    public static UsageTracker getInstance() {
    	if (INSTANCE != null) {
    		return INSTANCE;
    	} else {
            throw new RuntimeException("Applications need to call SalesforceSDKManager.init() first.");
    	}
    }

    public static void init(Context context) {
    	if (INSTANCE == null) {
    		INSTANCE = new UsageTracker(context);
    	} else {
    		Log.w("UsageTracker", "Called UsageTracker.init() more than once");
    	}
    }
    
    /**
     * Protected constructor
     * @param context
     */
    protected UsageTracker(Context context) {
    	int googlePlayServicesAvailable = GooglePlayServicesUtil.isGooglePlayServicesAvailable(context);
		if (googlePlayServicesAvailable == ConnectionResult.SUCCESS) {
    		Log.i("UsageTracker", "Setting up tracker");
			tracker = GoogleAnalytics.getInstance(context).newTracker(R.xml.sf__analytics);
    		tracker.setAppVersion(SalesforceSDKManager.SDK_VERSION);
    		String nativeOrHybrid = "native";
			if (SalesforceSDKManager.getInstance().isHybrid()) {
        	    BootConfig bootconfig = BootConfig.getBootConfig(context);
        	    nativeOrHybrid  = "Hybrid" + (bootconfig.isLocal() ? "Local" : "Remote");
    		}
    		trackerScreenPrefix = String.format("/%s/%s/", nativeOrHybrid, SalesforceSDKManager.getInstance().getAppNameAndVersion());
    	}
    	else {
    		Log.w("UsageTracker", "GooglePlayServices not available (connection result = " + googlePlayServicesAvailable);
    	}
    }
    
    /**
     * Report event
     */
    public void reportEvent(String category, String action) {
    	if (tracker != null) {
    		Log.i("UsageTracker", "Recording event: " + category + " " + action);
    		tracker.send(new HitBuilders.EventBuilder()
							.setCategory(category)
							.setAction(action)
							.build());
    	}
    }
    
    /**
     * Report screen view
     * @param params
     */
    public void reportScreenView(String screenName) {
    	if (tracker != null) {
    		Log.i("UsageTracker", "Recording screen view: " + screenName);
    		tracker.setScreenName(trackerScreenPrefix + screenName);
    		tracker.send(new HitBuilders.AppViewBuilder().build());
    	}
    }
}