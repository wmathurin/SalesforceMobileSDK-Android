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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.content.Context;

import com.localytics.android.LocalyticsSession;
import com.salesforce.androidsdk.app.SalesforceSDKManager;

public class LocalyticsUsageTracker extends UsageTracker {
	
	// Custom dimensions
	// "SDK_VERSION", "APP_TYPE", "APP_SUB_TYPE"

	// Custom attributes
	private static final String ORG_ID = "ORG_ID";
	private static final String APP_AGE = "APP_AGE";

	// Underlying locanalytics session
	private LocalyticsSession session;
	
	/* (non-Javadoc)
	 * @see com.salesforce.androidsdk.tracking.UsageTracker#init(android.content.Context)
	 */
	@Override
    public void init(Context context) {
		session = new LocalyticsSession(context, "efb187a85182b087d4553cc-b98299bc-c65a-11e3-99b4-005cf8cbabd8"); // XXX get from config file instead
    }
    
    /* (non-Javadoc)
     * @see com.salesforce.androidsdk.tracking.UsageTracker#reportEvent(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void reportEvent(String category, String action, String label) {
    	super.reportEvent(category, action, label);
    	session.tagEvent(category + "/" + action + "/" + label, getAttributes(), getCustomDimensions());
    }

	/* (non-Javadoc)
     * @see com.salesforce.androidsdk.tracking.UsageTracker#reportScreenView(android.app.Activity)
     */
    @Override
    public void reportScreenView(Activity activity) {
    	super.reportScreenView(activity);
		String screenName = activity.getClass().getSimpleName();
		session.open(getCustomDimensions());
		session.tagScreen(screenName);
		session.upload();
    }


    /* (non-Javadoc)
     * @see com.salesforce.androidsdk.tracking.UsageTracker#reportScreenViewEnd(android.app.Activity)
     */
    @Override
    public void reportScreenViewEnd(Activity activity) {
    	super.reportScreenViewEnd(activity);
		session.close(getCustomDimensions());
		session.upload();
    }

	private Map<String, String> getAttributes() {
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put(ORG_ID, getHashedOrgId());
		attributes.put(APP_AGE, "" + getAppAge());
		return attributes;
	}

	private List<String> getCustomDimensions() {
		List<String> customDimensions = new ArrayList<String>();
		customDimensions.add(SalesforceSDKManager.SDK_VERSION);
		customDimensions.add(getAppInfo().appType);
		customDimensions.add(getAppInfo().appSubType);
		return customDimensions;
	}
}