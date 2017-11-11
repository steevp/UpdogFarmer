package com.steevsapps.idledaddy.utils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class Utils {

    /**
     * Convert byte array to hex string
     * https://stackoverflow.com/a/9855338
     */
    public static String bytesToHex(byte[] bytes) {
        final char[] hexArray = "0123456789ABCDEF".toCharArray();

        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Convert ArrayList to comma-separated String
     */
    public static String arrayToString(List<String> list) {
        final StringBuilder builder = new StringBuilder();
        for (int i=0,size=list.size();i<size;i++) {
            final String string = list.get(i);
            builder.append(string);
            if (i + 1 < size) {
                builder.append(",");
            }
        }
        return builder.toString();
    }

    /**
     * Convert array to comma-separated String
     */
    public static String arrayToString(String[] array) {
        return arrayToString(Arrays.asList(array));
    }

    /**
     * Save Logcat to file
     */
    public static void saveLogcat(String path) {
        try {
            Runtime.getRuntime().exec("logcat -f " + path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
