package com.plaguevoice;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/** Lists and resolves the audio devices the user can route to/from. */
public final class DeviceUtils {

    public static final class Dev {
        public final int id;
        public final String label;
        public final int type;

        public Dev(int id, String label, int type) {
            this.id = id;
            this.label = label;
            this.type = type;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final int TYPE_BLE_HEADSET = 26; // AudioDeviceInfo.TYPE_BLE_HEADSET (API 31)

    private DeviceUtils() {
    }

    public static List<Dev> outputs(Context c) {
        List<Dev> list = new ArrayList<>();
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            return list;
        }
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (isOutputRelevant(d.getType())) {
                list.add(new Dev(d.getId(), pretty(d), d.getType()));
            }
        }
        return list;
    }

    public static List<Dev> inputs(Context c) {
        List<Dev> list = new ArrayList<>();
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            return list;
        }
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            if (isInputRelevant(d.getType())) {
                list.add(new Dev(d.getId(), pretty(d), d.getType()));
            }
        }
        return list;
    }

    public static AudioDeviceInfo find(Context c, int id, boolean output) {
        if (id < 0) {
            return null;
        }
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            return null;
        }
        AudioDeviceInfo[] all = am.getDevices(
                output ? AudioManager.GET_DEVICES_OUTPUTS : AudioManager.GET_DEVICES_INPUTS);
        for (AudioDeviceInfo d : all) {
            if (d.getId() == id) {
                return d;
            }
        }
        return null;
    }

    private static boolean isOutputRelevant(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_HEADSET:
            case TYPE_BLE_HEADSET:
                return true;
            default:
                return false;
        }
    }

    private static boolean isInputRelevant(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_MIC:
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_HEADSET:
                return true;
            default:
                return false;
        }
    }

    private static String pretty(AudioDeviceInfo d) {
        String name = "";
        try {
            CharSequence pn = d.getProductName();
            if (pn != null) {
                name = pn.toString();
            }
        } catch (Exception ignored) {
        }
        if (name.equals("null")) {
            name = "";
        }

        String kind;
        switch (d.getType()) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                kind = "phone speaker";
                break;
            case AudioDeviceInfo.TYPE_BUILTIN_MIC:
                kind = "phone mic";
                break;
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                kind = "Bluetooth speaker / stereo";
                break;
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                kind = "Bluetooth headset";
                break;
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                kind = "wired headphones";
                break;
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                kind = "wired headset";
                break;
            case AudioDeviceInfo.TYPE_USB_DEVICE:
                kind = "USB audio";
                break;
            case AudioDeviceInfo.TYPE_USB_HEADSET:
                kind = "USB headset";
                break;
            default:
                kind = "audio device";
                break;
        }
        return (name.isEmpty() ? kind : name + " (" + kind + ")");
    }

    public static boolean hasBluetoothConnect(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        return c.checkSelfPermission("android.permission.BLUETOOTH_CONNECT")
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }
}
