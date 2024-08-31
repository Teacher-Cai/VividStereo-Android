package com.example.vividstereo;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class TcpCommu {
    static public void receiveAndSend() {
        try {
            ServerSocket serverSocket = new ServerSocket(4001);
            Socket client = serverSocket.accept();
            client.setKeepAlive(true);
            PrintWriter pout = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));

            while (true) {
                String str = in.readLine();
                Long sTs = System.currentTimeMillis();
                if (Objects.equals(str, "return")) {
                    pout.print(System.currentTimeMillis());
                    pout.flush();
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    static public Map<String, Long> getDevicesDelay(List<String> ips) {
        Map<String, Long> ipWithDelays = new HashMap<>();
        int serverPort = 4000;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<Future<Long>> res = new ArrayList<>();

        try {
            for (String ip : ips) {
                res.add(executor.submit(new CallableJob(ip, serverPort)));
            }

            executor.shutdown();

            for (int i = 0; i < ips.size(); i++) {
                ipWithDelays.put(ips.get(i), res.get(i).get(5, TimeUnit.SECONDS));
            }
            ipWithDelays.put(GlobalInfo.getInstance().localIp, 0L);
        } catch (Exception e) {
            Log.e("tcp", "getDevicesDelay: ", e);
        }

        Log.d("ipWithDelays", "getDevicesDelay:" + ipWithDelays);

        return ipWithDelays;
    }

    static public Long aTcpClientDelay(String ip, int port) throws IOException {

        Socket clientSocket = new Socket(ip, port);
        clientSocket.setSoTimeout(3000);

        PrintWriter pout = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8));
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));

        List<Long> delays = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            pout.print("return\r\n");
            pout.flush();
            Long sendStartTs = System.currentTimeMillis();
            String str = in.readLine();
            Long sendEndTs = System.currentTimeMillis();

            Long transferTime = sendEndTs - sendStartTs;
//            Log.d("timing", "aTcpClientDelay: " + transferTime);

            Long estimateTimeDiff = sendEndTs - Long.parseLong(str) - transferTime / 2;

            if (transferTime < 10) {
                delays.add(estimateTimeDiff);
            }
        }
        clientSocket.close();
        return Double.valueOf(delays.stream().mapToDouble(Double::valueOf).average().getAsDouble()).longValue();
    }

    static class CallableJob implements Callable<Long> {

        public CallableJob(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }

        private final String ip;
        private final int port;

        @Override
        public Long call() throws Exception {
            return aTcpClientDelay(ip, port);
        }
    }
}
