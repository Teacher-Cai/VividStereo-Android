package com.example.vividstereo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GlobalInfo {
    private static GlobalInfo globalInfo;

    public Map<String, Long> ipWithDelay = new HashMap<>();

    public String localIp = "";

    public boolean masterDevice = true;

    public boolean synchronization = false;

    public List<String> allDeviceIp = new ArrayList<>();

    private GlobalInfo() {
    }

    public static GlobalInfo getInstance() {
        if (globalInfo == null) {
            globalInfo = new GlobalInfo();
        }
        return globalInfo;
    }
}
