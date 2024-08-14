package com.funkylogclient;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class UDPClient {
    public static String serverIP = "10.8.46.2";
    public static int port = 5808;

    public static void setConnectionAddress(String newServerIP, int newPort) {
        System.out.println("Setting connection address to " + newServerIP + ":" + newPort);
        serverIP = newServerIP;
        port = newPort;
    }

    private static final String ENCODING = "\nabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()[]<>|;:',./?~_- ";

    // private static Queue<String> recv_messages = new LinkedList<>();

    private static void threadFN() {
        try {
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(500);

            long lastKeepAlive = System.currentTimeMillis() - 4000;
            for (;;) {
                InetAddress serverAddress;
                try {
                    serverAddress = InetAddress.getByName(UDPClient.serverIP);
                } catch (Exception exc) {
                    continue;
                }
    
                long ctime = System.currentTimeMillis();
                if (ctime - lastKeepAlive > 500) {
                    byte[] sendData = "~~~".getBytes();
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, UDPClient.port);
                    socket.send(sendPacket);

                    lastKeepAlive = ctime;
                }

                byte[] receiveData = new byte[16800];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

                try {
                    socket.receive(receivePacket);
                } catch (SocketTimeoutException exc) {
                    continue;
                } catch (Exception exc) {
                    continue;
                }

                for (String line : parse(receivePacket.getData())) {
                    // UDPClient.recv_messages.add(line);
                    FunkyLogSorter.addMessage(new Message(line));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String decompress(byte[] input) {
        StringBuilder xarr = new StringBuilder();
        for (byte b : input) {
            String binaryStrX = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
            xarr.append(binaryStrX);
        }

        boolean capNext = false;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < xarr.length(); i += 6) {
            try {
                String bitSegment = xarr.substring(i, Math.min(i + 6, xarr.length()));
                int index = Integer.parseInt(bitSegment, 2);
                char nextChar = ENCODING.charAt(index);
                if (capNext) {
                    nextChar = Character.toUpperCase(nextChar);
                }
                capNext = false;
                out.append(nextChar);
            } catch (Exception e) {
                capNext = true;
            }
        }

        return out.toString();
    }

    private static String[] parse(byte[] input) {
        String decompressed = decompress(input);
        return decompressed.split("\n");
    }

    public static void start() {
        Thread networkingThread = new Thread(UDPClient::threadFN);
        networkingThread.setDaemon(true);
        networkingThread.start();
    }
}

