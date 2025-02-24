/*
 * Copyright (C) 2019 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.device;

import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.RemoteException;
import android.util.Log;
import android.view.IWindowManager;

import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.HashMap;

import vendor.nvidia.hardware.graphics.display.V1_0.HwcSvcDisplay;
import vendor.nvidia.hardware.graphics.display.V1_0.HwcSvcDisplayMode;
import vendor.nvidia.hardware.graphics.display.V1_0.HwcSvcDisplayModePixEnc;
import vendor.nvidia.hardware.graphics.display.V1_0.HwcSvcEdidInfo;
import vendor.nvidia.hardware.graphics.display.V1_0.INvDisplay;

public class DisplayUtils {
    private static final String TAG = DisplayUtils.class.getSimpleName();

    public static final String POWER_UPDATE_INTENT = "com.lineage.devicesettings.UPDATE_POWER";
 
    public static void setInternalDisplayState(boolean state) {
        Log.d(TAG, "setInternalDisplayState: " + String.valueOf(state));
        try {
            FileOutputStream colorModeFile = new FileOutputStream("/sys/bus/platform/devices/tegradc.0/enable");
            byte[] buf = new byte[2];

            buf[0] = (byte) (state ? '1' : '0');
            buf[1] = '\n';

            colorModeFile.write(buf);
            colorModeFile.close();
        } catch (IOException e) {
            Log.w(TAG, "Failed to write display state");
        }
    }

    public static void setDisplayMode(int display, INvDisplay displayService, IWindowManager windowManager, SharedPreferences sharedPrefs) {
        int index = 0; // default 0 for internal

        try {
            // grab pref for external displays
            if(display > 0) {
                String displayUid = String.valueOf(DisplayUtils.makeDisplayLabel(displayService.edidGetInfo(display), display).hashCode());
                index = Integer.parseInt(sharedPrefs.getString(("mode_" + displayUid), "0"));
            }

            // manually set hwc mode and force android to update rotation
            displayService.modeSetIndex(display, index);
            windowManager.updateRotation(true, true);
        } catch(RemoteException e) {
            Log.e(TAG, "Failed to set mode!");
        }
    }

    public static HashMap<String, Integer> makeUidMap(INvDisplay displayService) {
        HashMap<String, Integer> uidMap = new HashMap<String, Integer>();
        for (int i = HwcSvcDisplay.HWC_SVC_DISPLAY_PANEL; i <= HwcSvcDisplay.HWC_SVC_DISPLAY_HDMI2; i++) {
            try {
                String displayUid = String.valueOf(makeDisplayLabel(displayService.edidGetInfo(i), i).hashCode()); // A unique id we can use to identify the display
                uidMap.put(displayUid, i);
            } catch (RemoteException e) {
                continue;
            }
        }

        return uidMap;
    }

    public static String makeDisplayLabel(HwcSvcEdidInfo edid, int display) {
        if (!edid.monitor_name.isEmpty())
            return (edid.manufacturer_id.isEmpty() ? "" : (edid.manufacturer_id + " - ")) + edid.monitor_name; // Report name from edid if present
        if (display == HwcSvcDisplay.HWC_SVC_DISPLAY_PANEL)
            return "Internal Panel"; // Most internal panels do not have an edid

        return "Unknown";
    }

    public static String makeModeInfoString(HwcSvcDisplayMode mode) {
        return String.format("%dx%d %sHz", mode.xres, mode.yres, new DecimalFormat("###.##").format(mode.refresh));
    }

    public static String makeColorInfoString(HwcSvcDisplayMode mode) {
        String encodingStr = "";
        String colorimetryStr = "";

        switch (mode.pixenc) {
            case HwcSvcDisplayModePixEnc.HWC_SVC_DISP_PIXENC_YUV444:
                encodingStr = "YUV444";
                break;
            case HwcSvcDisplayModePixEnc.HWC_SVC_DISP_PIXENC_YUV422:
                encodingStr = "YUV422";
                break;
            case HwcSvcDisplayModePixEnc.HWC_SVC_DISP_PIXENC_YUV420:
                encodingStr = "YUV420";
                break;
            default:
                encodingStr = "RGB";
                break;
        }

        if (mode.colorimetry != 1)
            colorimetryStr = "Rec. 709";
        else
            colorimetryStr = "Rec. 2020";

        return String.format("%s %d-bit %s", encodingStr, mode.bpc, colorimetryStr);
    }

    public static void setPanelColorMode(String mode) {
        Log.d(TAG, "OLED panel mode set: " + mode);
        try {
            FileOutputStream colorModeFile = new FileOutputStream("/sys/devices/50000000.host1x/tegradc.0/panel_color_mode");
            byte[] buf = new byte[4];

            buf = mode.getBytes(StandardCharsets.US_ASCII);

            colorModeFile.write(buf);
            colorModeFile.close();
        } catch (IOException e) {
            Log.w(TAG, "Failed to write color mode");
        }
    }

    public static String getPanelColorMode() {
        byte[] buf = new byte[4];
        String out;

        try {
            FileInputStream colorModeFile = new FileInputStream("/sys/devices/50000000.host1x/tegradc.0/panel_color_mode");

            colorModeFile.read(buf);
            out = new String(buf, StandardCharsets.US_ASCII);
            Log.w(TAG, "OLED mode read color mode: " + out);
            colorModeFile.close();
        } catch (IOException e) {
            Log.w(TAG, "Failed to read color mode");
            out = new String("");
        }

        return out;
    }

    public static void setFanProfile(String profile) {
        Log.i(TAG, "Setting fan profile: " + profile);
        try {
            final FileOutputStream pwmProfile = new FileOutputStream("/sys/devices/pwm-fan/fan_profile");
            pwmProfile.write(profile.getBytes());
            pwmProfile.close();
            final FileOutputStream estProfile = new FileOutputStream("/sys/devices/thermal-fan-est/fan_profile");
            estProfile.write(profile.getBytes());
            estProfile.close();
        } catch (IOException e) {
            Log.w(TAG, "Failed to update fan profile");
        }
    }
}
