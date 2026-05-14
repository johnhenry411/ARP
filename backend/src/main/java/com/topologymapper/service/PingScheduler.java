package com.topologymapper.service;

import com.topologymapper.model.*;
import com.topologymapper.service.NetworkScannerService.PingResult;
import java.util.ArrayList;
import com.topologymapper.store.TopologyStore;
import com.topologymapper.websocket.WebSocketBroadcaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
public class PingScheduler {

    @Autowired private TopologyStore store;
    @Autowired private WebSocketBroadcaster broadcaster;
    @Autowired private NetworkScannerService scanner;
    @Autowired private SnmpService snmpService;
    @Autowired private ThreatDetectionService threatDetectionService;

    private static final int LATENCY_HISTORY          = 10;
    private static final int HIGH_LATENCY_THRESHOLD   = 100; // ms — real pings are higher than simulated ones
    private static final DateTimeFormatter TIME_FMT   = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void runScanCycle() {
        log.info("Scan cycle started — pinging {} devices", store.allDevices().size());

        // Ping all devices in parallel
        ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(32, Math.max(1, store.allDevices().size())));

        List<Future<DeviceUpdate>> futures = new ArrayList<>();
        for (Device device : store.allDevices()) {
            futures.add(pool.submit(() -> pingDevice(device)));
        }
        pool.shutdown();
        try { pool.awaitTermination(20, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

        List<Device> camOnline = new ArrayList<>();
        List<String> wentOffline = new ArrayList<>();

        for (Future<DeviceUpdate> f : futures) {
            try {
                DeviceUpdate u = f.get();
                if (u.statusChanged) {
                    if (u.newStatus == DeviceStatus.OFFLINE) {
                        wentOffline.add(u.device.getId());
                        createAlert(AlertType.DEVICE_LOST, u.device.getId(),
                                u.device.getHostname() + " went offline");
                    } else {
                        camOnline.add(u.device);
                        createAlert(AlertType.DEVICE_JOINED, u.device.getId(),
                                u.device.getHostname() + " came back online");
                    }
                }
                checkHighLatency(u.device);
            } catch (Exception e) {
                log.warn("Error processing ping result: {}", e.getMessage());
            }
        }

        log.info("Scan complete — {} came online, {} went offline", camOnline.size(), wentOffline.size());
        broadcaster.broadcastScanResult(camOnline, wentOffline);
    }

    public void runScanCycleNow() { runScanCycle(); }

    // -----------------------------------------------------------------------
    // Per-device ping + update
    // -----------------------------------------------------------------------

    private DeviceUpdate pingDevice(Device device) {
        DeviceStatus prev = device.getStatus();
        PingResult result = scanner.ping(device.getIp());
        DeviceStatus next = result.isReachable() ? DeviceStatus.ONLINE : DeviceStatus.OFFLINE;

        // Promote NEW → ONLINE once we confirm it's reachable
        if (prev == DeviceStatus.NEW && result.isReachable()) next = DeviceStatus.ONLINE;

        device.setStatus(next);

        if (!result.isReachable()) {
            device.setLatencies(new ArrayList<>());
            device.setBandwidth(0);
        } else {
            // Update latency history with real measurement
            List<Integer> lat = new ArrayList<>(device.getLatencies());
            lat.add(result.getLatencyMs());
            if (lat.size() > LATENCY_HISTORY) lat.remove(0);
            device.setLatencies(lat);

            // Bandwidth: prefer SNMP if configured, otherwise keep last known value
            updateBandwidth(device);
        }

        // Auto threat detection
        ThreatDetectionService.ThreatResult threat = threatDetectionService.evaluate(device);
        if (threat.level() != device.getThreatLevel()) {
            device.setThreatLevel(threat.level());
            device.setThreatReason(threat.reason());
            if (threat.level() != ThreatLevel.none) {
                createAlert(AlertType.THREAT_DETECTED, device.getId(),
                        device.getHostname() + ": " + threat.reason());
            }
        }

        store.putDevice(device);

        boolean changed = (prev != next) && !(prev == DeviceStatus.NEW && next == DeviceStatus.ONLINE);
        return new DeviceUpdate(device, next, changed);
    }

    private void updateBandwidth(Device device) {
        if (device.getSnmpCommunity() == null) return; // no SNMP configured

        int bwKbps = snmpService.getBandwidthKbps(
                device.getId(),
                device.getIp(),
                device.getSnmpCommunity(),
                device.getSnmpPort() > 0 ? device.getSnmpPort() : 161);

        if (bwKbps >= 0) {
            device.setBandwidth(bwKbps);
            List<Integer> hist = new ArrayList<>(device.getBandwidthHistory());
            hist.add(bwKbps);
            if (hist.size() > 20) hist.remove(0);
            device.setBandwidthHistory(hist);
        }
        // -1 means SNMP failed — leave bandwidth unchanged
    }

    private void checkHighLatency(Device device) {
        if (device.getLatencies().isEmpty()) return;
        int max = device.getLatencies().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (max > HIGH_LATENCY_THRESHOLD) {
            createAlert(AlertType.HIGH_LATENCY, device.getId(),
                    device.getHostname() + " " + max + "ms spike");
        }
    }

    private void createAlert(AlertType type, String deviceId, String message) {
        store.addAlert(Alert.builder()
                .id(store.nextAlertId())
                .type(type)
                .deviceId(deviceId)
                .message(message)
                .timestamp(LocalTime.now().format(TIME_FMT))
                .build());
    }

    // -----------------------------------------------------------------------

    private record DeviceUpdate(Device device, DeviceStatus newStatus, boolean statusChanged) {}
}
