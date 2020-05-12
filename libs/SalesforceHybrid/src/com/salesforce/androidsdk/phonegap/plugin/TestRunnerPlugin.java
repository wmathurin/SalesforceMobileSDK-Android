/*
 * Copyright (c) 2011-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.phonegap.plugin;

import android.webkit.WebView;
import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger;
import com.salesforce.nimbus.BoundMethod;
import com.salesforce.nimbus.Bridge;
import com.salesforce.nimbus.Plugin;
import com.salesforce.nimbus.PluginOptions;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Nimbus plugin to run javascript tests.
 */
@PluginOptions(name="TestRunnerPlugin")
public class TestRunnerPlugin implements Plugin {

	private static final String TAG = "TestRunnerPlugin";
	
	// To synchronize with the tests
	public final static BlockingQueue<Boolean> readyForTests = new ArrayBlockingQueue<Boolean>(1);
	public final static BlockingQueue<TestResult> testResults = new ArrayBlockingQueue<TestResult>(1);

	@Override
	public void cleanup(WebView webView, Bridge bridge) {
	}

	@Override
	public void customize(WebView webView, Bridge bridge) {
	}

	@BoundMethod
	public void onTestComplete(String testName, boolean success, String rawMessage, int durationMsec) {
		String message = stripHtml(rawMessage);
        double duration = durationMsec / 1000.0;
		TestResult testResult = new TestResult(testName, success, message, duration);
		testResults.add(testResult);
        SalesforceHybridLogger.w(TAG, testResult.testName + " completed in " + testResult.duration);
	}

	private String stripHtml(String message) {
		return message.replaceAll("<[^>]+>", "|").replaceAll("[|]+"," ");
	}
	
	@BoundMethod
	public void onReadyForTests() {
		readyForTests.add(Boolean.TRUE);
	}
	
	public static class TestResult {
		public final String testName;
		public final boolean success;
		public final String message;
		public final double duration; //time in seconds
		
		public TestResult(String testName, boolean success, String message, double duration) {
			this.testName = testName;
			this.success = success;
			this.message = message;
			this.duration = duration;
		}
	}
}
