package com.hxkiosk;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;

import androidx.annotation.NonNull;

import java.net.Inet4Address;
import java.net.InetAddress;

public final class LanAddressHelper {

    private LanAddressHelper() {
    }

    @NonNull
    public static String getIpv4Address(@NonNull Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return "";
        }
        Network network = connectivityManager.getActiveNetwork();
        if (network == null) {
            return "";
        }
        LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
        if (linkProperties == null) {
            return "";
        }
        for (LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
            InetAddress address = linkAddress.getAddress();
            if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                return address.getHostAddress();
            }
        }
        return "";
    }
}
