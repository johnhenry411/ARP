package com.topologymapper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Passively tracks IP→MAC mappings from ARP table reads.
 * Flags MAC address changes (potential ARP spoofing) and
 * duplicate MACs shared across multiple IPs.
 */
@Slf4j
@Service
public class ArpMonitorService {

    @Autowired private NetworkScannerService scanner;

    // The last known MAC for each IP
    private final Map<String, String> knownMacs = new ConcurrentHashMap<>();

    /**
     * Called per-device during security scan. Returns a list of human-readable
     * anomaly descriptions, empty list if nothing suspicious.
     */
    public List<String> checkDevice(String ip, String currentMac) {
        List<String> anomalies = new ArrayList<>();

        String previousMac = knownMacs.put(ip, currentMac);
        if (previousMac != null
                && !previousMac.equals("FF:FF:FF:FF:FF:FF")
                && !previousMac.equalsIgnoreCase(currentMac)) {
            String msg = "MAC changed: " + previousMac + " → " + currentMac
                + " — possible ARP spoofing or hardware replacement";
            log.warn("ARP anomaly on {}: {}", ip, msg);
            anomalies.add(msg);
        }

        // Check for duplicate MAC across all known IPs
        String finalCurrentMac = currentMac;
        knownMacs.entrySet().stream()
            .filter(e -> !e.getKey().equals(ip)
                && e.getValue().equalsIgnoreCase(finalCurrentMac)
                && !finalCurrentMac.equals("FF:FF:FF:FF:FF:FF"))
            .findFirst()
            .ifPresent(e -> {
                String msg = "MAC " + finalCurrentMac + " also seen on " + e.getKey()
                    + " — possible ARP spoofing (one MAC, two IPs)";
                log.warn("ARP duplicate MAC on {}: {}", ip, msg);
                anomalies.add(msg);
            });

        return anomalies;
    }

    /**
     * Full ARP table refresh — reads the system ARP table and updates the
     * known MAC table. Returns any anomalies found across the whole table.
     */
    public List<String> refreshArpTable() {
        Map<String, String> arpTable = scanner.readArpTable();
        List<String> anomalies = new ArrayList<>();

        // Check for duplicate MACs — one MAC, multiple IPs (classic ARP spoof sign)
        Map<String, List<String>> macToIps = new HashMap<>();
        for (Map.Entry<String, String> entry : arpTable.entrySet()) {
            macToIps.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }
        for (Map.Entry<String, List<String>> entry : macToIps.entrySet()) {
            String mac = entry.getKey();
            List<String> ips = entry.getValue();
            if (ips.size() > 1 && !mac.equals("FF:FF:FF:FF:FF:FF")) {
                anomalies.add("Duplicate MAC " + mac + " seen on " + String.join(", ", ips)
                    + " — potential ARP poisoning");
            }
        }

        // Check for MAC changes vs last known state
        for (Map.Entry<String, String> entry : arpTable.entrySet()) {
            String ip  = entry.getKey();
            String mac = entry.getValue();
            String prev = knownMacs.get(ip);
            if (prev != null && !prev.equals("FF:FF:FF:FF:FF:FF") && !prev.equalsIgnoreCase(mac)) {
                anomalies.add(ip + " MAC changed: " + prev + " → " + mac);
            }
            knownMacs.put(ip, mac);
        }

        return anomalies;
    }
}
