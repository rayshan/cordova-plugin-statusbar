/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
*/
package org.apache.cordova.statusbar;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONException;

public class StatusBar extends CordovaPlugin {
    private static final String TAG = "StatusBar";
    private CallbackContext callbackContext;
    private Integer uiOptionsSnapshot;
    private Window window;

    /**
     * Sets the context of the Command. This can then be used to do things like
     * get file paths associated with the Activity.
     *
     * @param cordova The context of the main Activity.
     * @param webView The CordovaWebView Cordova is running in.
     */
    @Override
    public void initialize(final CordovaInterface cordova, CordovaWebView webView) {
        Log.v(TAG, "StatusBar: initialization");
        super.initialize(cordova, webView);
        final Window window = cordova.getActivity().getWindow();
        this.window = window;

        this.cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Clear flag FLAG_FORCE_NOT_FULLSCREEN which is set initially by the Cordova.
                window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);

                // Read 'StatusBarBackgroundColor' from config.xml, default is #000000.
                setStatusBarBackgroundColor(preferences.getString("StatusBarBackgroundColor", "#000000"));
            }
        });
    }

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action            The action to execute.
     * @param args              JSONArry of arguments for the plugin.
     * @param callbackContext   The callback id used when calling back into JavaScript.
     * @return                  True if the action was valid, false otherwise.
     */
    @Override
    public boolean execute(final String action, final CordovaArgs args, final CallbackContext callbackContext) throws JSONException {
        Log.v(TAG, "Executing action: " + action);
        final Activity activity = this.cordova.getActivity();
        final Window window = this.window;
        this.callbackContext = callbackContext;

        if ("_ready".equals(action)) {
            boolean statusBarVisible = (this.window.getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) == 0;
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, statusBarVisible));
            return true;
        }

        if ("show".equals(action)) {
            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // SYSTEM_UI_FLAG_FULLSCREEN is available since JellyBean, but we
                    // use KitKat here to be aligned with "Fullscreen"  preference
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        int uiOptions = window.getDecorView().getSystemUiVisibility();
                        uiOptions &= ~View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                        uiOptions &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;

                        window.getDecorView().setSystemUiVisibility(uiOptions);
                    }

                    // CB-11197 We still need to update LayoutParams to force status bar
                    // to be hidden when entering e.g. text fields
                    window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                }
            });
            return true;
        }

        if ("hide".equals(action)) {
            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // SYSTEM_UI_FLAG_FULLSCREEN is available since JellyBean, but we
                    // use KitKat here to be aligned with "Fullscreen"  preference
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        int uiOptions = window.getDecorView().getSystemUiVisibility()
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_FULLSCREEN;

                        window.getDecorView().setSystemUiVisibility(uiOptions);
                    }

                    // CB-11197 We still need to update LayoutParams to force status bar
                    // to be hidden when entering e.g. text fields
                    window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                }
            });
            return true;
        }

        if ("backgroundColorByHexString".equals(action)) {
            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        setStatusBarBackgroundColor(args.getString(0));
                    } catch (JSONException ignore) {
                        Log.e(TAG, "Invalid hexString argument, use f.i. '#777777'");
                    }
                }
            });
            return true;
        }

        return false;
    }

    private void clearTranslucentSetting() {
        this.window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
    }

    private void clearTransparentSetting() {
        if (this.uiOptionsSnapshot != null) {
            this.window.getDecorView().setSystemUiVisibility(this.uiOptionsSnapshot.intValue());
            this.uiOptionsSnapshot = null;
        }
        this.window.clearFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
    }

    private void setStatusBarBackgroundColor(final String colorPref) {
        // Determined by setStatusBarColor & FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
        final boolean canSetToTransparent = Build.VERSION.SDK_INT >= 21; // Lollipop
        // Determined by FLAG_TRANSLUCENT_STATUS
        final boolean canSetToTranslucent = Build.VERSION.SDK_INT >= 19; // KitKat

        Log.w(TAG, "colorPref = " + colorPref);
        if (colorPref != null && !colorPref.isEmpty()) {

            if ("transparent".equals(colorPref) && canSetToTransparent) {
                if (canSetToTransparent) {
                    Log.w(TAG, "Setting to transparent");
                    if (canSetToTranslucent) {
                        this.clearTranslucentSetting();
                    }
                    this.uiOptionsSnapshot = new Integer(this.window.getDecorView().getSystemUiVisibility());
                    // Draw behind status bar per http://stackoverflow.com/a/28041425/2152076
                    this.window.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
                    this.window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                    this.window.setStatusBarColor(Color.TRANSPARENT);
                } else {
                    Log.w(TAG, "Setting to transparent not supported. Falling back to translucent.");
                }

            } else if (("translucent".equals(colorPref) && canSetToTranslucent) ||
                ("transparent".equals(colorPref) && !canSetToTransparent)) {
                if (canSetToTransparent) {
                    this.clearTransparentSetting();
                }
                if (canSetToTranslucent) {
                    Log.w(TAG, "Setting to translucent");
                    this.window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                } else {
                    Log.w(TAG, "Setting to transparent & translucent not supported. Falling back to solid color.");
                }

            } else {
                Log.w(TAG, "Setting to solid color");
                final String colorPrefFallback = 
                    preferences.getString("StatusBarBackgroundColorFallback", null);
                if (canSetToTransparent) {
                    this.clearTransparentSetting();
                }
                if (canSetToTranslucent) {
                    this.clearTranslucentSetting();
                }
                // Method and constants not available on all SDKs but we want to be able to compile this code with any SDK
                this.window.clearFlags(0x04000000); // SDK 19: WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                this.window.addFlags(0x80000000); // SDK 21: WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                try {
                    // Using reflection makes sure any 5.0+ device will work without having to compile with SDK level 21
                    this.window.getClass()
                        .getDeclaredMethod("setStatusBarColor", int.class)
                        .invoke(
                            this.window,
                            Color.parseColor(colorPrefFallback != null ? colorPrefFallback : colorPref)
                        );
                } catch (IllegalArgumentException ignore) {
                    Log.e(TAG, "Invalid hexString argument, use f.i. '#999999'");
                } catch (NoSuchMethodException ignore) {
                    Log.w(TAG, "Method window.setStatusBarColor not found for SDK level " + Build.VERSION.SDK_INT);
                } catch (Exception e) {
                    Log.e(TAG, "exception", e);
                }
            }

        }
    }
}
