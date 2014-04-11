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
package com.salesforce.androidsdk.phonegap;

import java.util.Collection;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.RemoteException;
import android.util.Log;

import com.radiusnetworks.ibeacon.IBeacon;
import com.radiusnetworks.ibeacon.IBeaconConsumer;
import com.radiusnetworks.ibeacon.IBeaconManager;
import com.radiusnetworks.ibeacon.RangeNotifier;
import com.radiusnetworks.ibeacon.Region;

/**
 * PhoneGap plugin for ibeacon functionality
 */
public class IBeaconManagerPlugin extends ForcePlugin implements IBeaconConsumer {
	
	// JSON fields in region / ibeacon
	private static final String MINOR_FIELD = "minor";
	private static final String MAJOR_FIELD = "major";
	private static final String UUID_FIELD = "uuid";
	private static final String PROXIMITY_FIELD = "proximity";

	/**
	 * Supported plugin actions that the client can take.
	 */
	enum Action {
		startRanging, stopRanging
	}

	private IBeaconManager iBeaconManager;

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);
		iBeaconManager = IBeaconManager.getInstanceForApplication(cordova
				.getActivity());
	}

	@Override
	public boolean execute(String actionStr, JavaScriptPluginVersion jsVersion,
			JSONArray args, CallbackContext callbackContext)
			throws JSONException {
		// Figure out action
		Action action = null;
		try {
			action = Action.valueOf(actionStr);
			switch (action) {
			case startRanging:
				startRanging(args, callbackContext);
				return true;
			case stopRanging:
				stopRanging(args, callbackContext);
				return true;
			default:
				return false;
			}
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/**
	 * Native implementation for "startRanging" action.
	 * 
	 * @param callbackContext
	 *            Used when calling back into Javascript.
	 * @throws JSONException
	 */
	protected void startRanging(JSONArray args,
			final CallbackContext callbackContext) throws JSONException {
		Log.i("IBeaconPlugin.startRanging", "startRanging called");
		iBeaconManager.bind(this);
		callbackContext.success();
	}

	/**
	 * Native implementation for "startRanging" action.
	 * 
	 * @param callbackContext
	 *            Used when calling back into Javascript.
	 * @throws JSONException
	 */
	protected void stopRanging(JSONArray args,
			final CallbackContext callbackContext) throws JSONException {
		Log.i("IBeaconPlugin.startRanging", "startRanging called");
		iBeaconManager.unBind(this);
		callbackContext.success();
	}

	@Override
	public void onIBeaconServiceConnect() {
		iBeaconManager.setRangeNotifier(new RangeNotifier() {
			@Override
			public void didRangeBeaconsInRegion(Collection<IBeacon> iBeacons, Region region) {
				try {
					JSONArray json = jsonForIBeacons(iBeacons);
			        webView.loadUrl("javascript:cordova.fireDocumentEvent('didRangeBeaconsInRegion', " + json.toString() + ");");
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					Log.e("IBeaconPlugin.didRangeBeaconsInRegion", e.getMessage(), e);
				}
			}
		});

		try {
			iBeaconManager.startRangingBeaconsInRegion(new Region(
					"myRangingUniqueId", null, null, null));
		} catch (RemoteException e) {
		}
	}

	@Override
	public boolean bindService(Intent intent, ServiceConnection connection, int mode) {
		return cordova.getActivity().bindService(intent, connection, mode);
	}

	@Override
	public Context getApplicationContext() {
		return cordova.getActivity().getApplicationContext();
	}

	@Override
	public void unbindService(ServiceConnection connection) {
		cordova.getActivity().unbindService(connection);
	}

	
   /**************************************************************************************************
    *
    * Helper methods
    *
    **************************************************************************************************/
	
	/**
	 * Return JSONObject for region
	 * @param region
	 * @return
	 */
	public JSONObject jsonForRegion(Region region) throws JSONException  {
		JSONObject json = new JSONObject();
		json.put(UUID_FIELD, region.getProximityUuid());
		json.put(MAJOR_FIELD,  region.getMajor());
		json.put(MINOR_FIELD, region.getMinor());
		return json;
	}

	/**
	 * Return JSONObject for iBeacon
	 * @param iBeacon
	 * @return
	 */
	public JSONObject jsonForIBeacon(IBeacon beacon) throws JSONException  {
		JSONObject json = new JSONObject();
		
		json.put(UUID_FIELD, beacon.getProximityUuid());
		json.put(MAJOR_FIELD, beacon.getMajor());
		json.put(MINOR_FIELD, beacon.getMinor());
		json.put(PROXIMITY_FIELD,  beacon.getProximity());
		// TODO rest of the fields
		return json;
	}

	/**
	 * @param beacons
	 * @return
	 * @throws JSONException
	 */
	public JSONArray jsonForIBeacons(Collection<IBeacon> beacons) throws JSONException {
		JSONArray json = new JSONArray();
		for (IBeacon beacon : beacons) {
			json.put(jsonForIBeacon(beacon));
		}
		return json;
	}
}
