package com.example.vividstereo;

import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

public class UdpCommu {
    private static UdpCommu udpCommu = null;

    private final int portNum = 4002;

    private DatagramSocket datagramSocket;

    private UdpCommu() {
        try {
            datagramSocket = new DatagramSocket(portNum);
            datagramSocket.setBroadcast(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static UdpCommu getInstance() {
        if (udpCommu == null) {
            udpCommu = new UdpCommu();
        }
        return udpCommu;
    }

    public void send(String message, String netAddress) {
        try {
            Log.d("udp", "send: " + message);
            byte[] buf = message.getBytes();
            InetAddress inetAddress = InetAddress.getByName(netAddress);
            DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length, inetAddress, portNum);
            datagramSocket.send(datagramPacket);
        } catch (Exception e) {
            Log.e("udp", "send: ", e);
            e.printStackTrace();
        }
    }

    public String listen() {
        try {
            byte[] buf = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(buf, buf.length);
            datagramSocket.receive(receivePacket);
            String receiveStr = new String(receivePacket.getData(), 0, receivePacket.getLength());
            return receiveStr;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getLocalIp() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        Log.d("hey", "getLocalIp: "+inetAddress.getHostAddress());
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return "";
    }

}
