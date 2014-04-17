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

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.app.SalesforceSDKManager;

public class GoogleAnalyticsUsageTracker extends UsageTracker {
	
	// Make sure to create the following custom dimensions for you property
	// Indexes for custom dimensions 
	private static final int SDK_VERSION = 1;
	private static final int APP_TYPE = 2;
	private static final int APP_SUB_TYPE = 3;
	private static final int ORG_ID = 4;

	// Make sure to create the following custom metrics for you property
	// Indexes for custom metrics
	private static final int APP_AGE = 1;

	// Underlying google analytics tracker
	private Tracker tracker;
	
	/* (non-Javadoc)
	 * @see com.salesforce.androidsdk.tracking.UsageTracker#init(android.content.Context)
	 */
	@Override
    public void init(Context context) {
    	int available = GooglePlayServicesUtil.isGooglePlayServicesAvailable(context);
		if (available != ConnectionResult.SUCCESS) {
    		throw new RuntimeException("GooglePlayServices not available (connection result = " + available);
    	}
		tracker = GoogleAnalytics.getInstance(context).newTracker(R.xml.sf__analytics);
    }
    
    /* (non-Javadoc)
     * @see com.salesforce.androidsdk.tracking.UsageTracker#reportEvent(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void reportEvent(String category, String action, String label) {
    	super.reportEvent(category, action, label);
		tracker.send(new HitBuilders.EventBuilder()
							.setCategory(category)
							.setAction(action)
							.setLabel(label)
							.setCustomDimension(SDK_VERSION, SalesforceSDKManager.SDK_VERSION)
							.setCustomDimension(APP_TYPE, getAppInfo().appType)
							.setCustomDimension(APP_SUB_TYPE, getAppInfo().appSubType)
							.setCustomDimension(ORG_ID, getHashedOrgId())
							.setCustomMetric(APP_AGE, getAppAge())
							.build());
    }
    
    /* (non-Javadoc)
     * @see com.salesforce.androidsdk.tracking.UsageTracker#reportScreenView(android.app.Activity)
     */
    @Override
    public void reportScreenView(Activity activity) {
    	super.reportScreenView(activity);
		String screenName = activity.getClass().getSimpleName();
		tracker.setScreenName(screenName);
		tracker.send(new HitBuilders.AppViewBuilder()
						.setCustomDimension(SDK_VERSION, SalesforceSDKManager.SDK_VERSION)
						.setCustomDimension(APP_TYPE, getAppInfo().appType)
						.setCustomDimension(APP_SUB_TYPE, getAppInfo().appSubType)
						.setCustomDimension(ORG_ID, getHashedOrgId())
						.setCustomMetric(APP_AGE, getAppAge())
						.build());
    }


    /* (non-Javadoc)
     * @see com.salesforce.androidsdk.tracking.UsageTracker#reportScreenViewEnd(android.app.Activity)
     */
    @Override
    public void reportScreenViewEnd(Activity activity) {
    	super.reportScreenViewEnd(activity);
    }
}