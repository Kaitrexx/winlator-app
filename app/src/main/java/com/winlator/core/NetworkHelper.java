package com.winlator.core;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.system.OsConstants;

import androidx.annotation.NonNull;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class NetworkHelper {
    private final ConnectivityManager connectivityManager;

    public static class IFAddress {
        public String name = "eth0";
        public int flags = 0;
        public int family = OsConstants.AF_INET;
        public int scopeId = 0;
        public String address = "0";
        public String netmask = "0";

        @NonNull
        @Override
        public String toString() {
            return name+","+flags+","+family+","+scopeId+","+address+","+netmask;
        }
    }

    public NetworkHelper(Context context) {
        connectivityManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    // Returns the networks that have link properties: regular networks (Wi-Fi, Ethernet, mobile) first, VPN networks (ZeroTier etc.) after them.
    private List<Network> getOrderedNetworks() {
        ArrayList<Network> regular = new ArrayList<>();
        ArrayList<Network> vpn = new ArrayList<>();

        Network[] networks = connectivityManager.getAllNetworks();
        if (networks != null) {
            for (Network network : networks) {
                if (connectivityManager.getLinkProperties(network) == null) continue;
                if (isVpn(network)) vpn.add(network);
                else regular.add(network);
            }
        }

        ArrayList<Network> result = new ArrayList<>(regular);
        result.addAll(vpn);
        return result;
    }

    private boolean isVpn(Network network) {
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    }

    public String getIPv4Address() {
        for (Network network : getOrderedNetworks()) {
            LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
            if (linkProperties == null) continue;

            for (LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
                InetAddress address = linkAddress.getAddress();
                if (address instanceof Inet4Address && !address.isLoopbackAddress()) return address.getHostAddress();
            }
        }
        return null;
    }

    public List<IFAddress> getIFAddresses() {
        ArrayList<IFAddress> result = new ArrayList<>();

        int index = 0;
        for (Network network : getOrderedNetworks()) {
            LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
            if (linkProperties == null) continue;

            // First network keeps the old name (eth0), additional ones (e.g. ZeroTier) become eth1, eth2...
            String name = "eth"+index;
            boolean added = false;

            for (LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
                InetAddress address = linkAddress.getAddress();
                if (!(address instanceof Inet4Address || address instanceof Inet6Address)) continue;
                if (address.isLoopbackAddress()) continue;

                IFAddress ifAddress = new IFAddress();
                ifAddress.name = name;
                ifAddress.flags = OsConstants.IFF_UP | OsConstants.IFF_RUNNING | OsConstants.IFF_MULTICAST;

                if (address instanceof Inet6Address) {
                    ifAddress.family = OsConstants.AF_INET6;
                    ifAddress.scopeId = ((Inet6Address)address).getScopeId();
                }
                else ifAddress.flags |= OsConstants.IFF_BROADCAST;

                ifAddress.address = address.getHostAddress();
                ifAddress.netmask = formatNetmask(linkAddress.getPrefixLength(), address instanceof Inet6Address);

                result.add(ifAddress);
                added = true;
            }
            if (added) index++;
        }
        return result;
    }

    public static String formatIpAddress(int ipAddress) {
        return (ipAddress & 255)+"."+((ipAddress >> 8) & 255)+"."+((ipAddress >> 16) & 255)+"."+((ipAddress >> 24) & 255);
    }

    public static String formatNetmask(int prefixLength) {
        return formatNetmask(prefixLength, prefixLength > 32);
    }

    // Works for any prefix length (/20, /22 ...), not only 8/16/24/32/64.
    public static String formatNetmask(int prefixLength, boolean ipv6) {
        if (!ipv6) {
            if (prefixLength < 0) prefixLength = 0;
            if (prefixLength > 32) prefixLength = 32;
            long mask = prefixLength == 0 ? 0 : (0xffffffffL << (32 - prefixLength)) & 0xffffffffL;
            return ((mask >> 24) & 255)+"."+((mask >> 16) & 255)+"."+((mask >> 8) & 255)+"."+(mask & 255);
        }

        if (prefixLength == 64) return "ffff:ffff:ffff:ffff::"; // same string as before
        if (prefixLength < 0) prefixLength = 0;
        if (prefixLength > 128) prefixLength = 128;
        StringBuilder sb = new StringBuilder();
        int remaining = prefixLength;
        for (int i = 0; i < 8; i++) {
            int bits = Math.min(16, Math.max(0, remaining));
            remaining -= bits;
            int group = bits == 0 ? 0 : (0xffff << (16 - bits)) & 0xffff;
            if (i > 0) sb.append(":");
            sb.append(Integer.toHexString(group));
        }
        return sb.toString();
    }

    public boolean isConnected() {
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo == null) return false;
        int type = networkInfo.getType();
        return networkInfo.isAvailable() && networkInfo.isConnectedOrConnecting() && (type == ConnectivityManager.TYPE_WIFI || type == ConnectivityManager.TYPE_ETHERNET  || type == ConnectivityManager.TYPE_MOBILE);
    }
}
