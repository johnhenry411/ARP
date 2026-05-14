package com.topologymapper.service;

import com.topologymapper.model.*;
import com.topologymapper.service.NetworkScannerService.DiscoveredHost;
import com.topologymapper.store.TopologyStore;
import com.topologymapper.websocket.WebSocketBroadcaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scans a gateway's subnet for live hosts and auto-registers any
 * newly discovered devices under that gateway.
 */
@Slf4j
@Service
public class SubnetScanService {

    @Autowired private NetworkScannerService scanner;
    @Autowired private SnmpService snmpService;
    @Autowired private TopologyStore store;
    @Autowired private WebSocketBroadcaster broadcaster;
    @Autowired private SecurityScanService securityScanService;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Runs asynchronously after a gateway is added. Sweeps the subnet and
     * registers any new devices it finds under this gateway.
     */
    @Async
    public void scanGatewaySubnet(Device gateway) {
        String subnet = resolveSubnet(gateway);
        if (subnet == null) {
            log.info("No subnet for gateway {} — skipping subnet scan", gateway.getHostname());
            return;
        }

        log.info("Starting subnet scan for gateway {} on {}", gateway.getHostname(), subnet);

        // Prefer SNMP ARP table if configured — faster and more accurate than ping sweep
        Map<String, String> discovered = new LinkedHashMap<>();
        if (gateway.getSnmpCommunity() != null) {
            log.info("Using SNMP ARP table for {}", gateway.getIp());
            discovered = snmpService.getConnectedDevices(
                    gateway.getIp(), gateway.getSnmpCommunity(),
                    gateway.getSnmpPort() > 0 ? gateway.getSnmpPort() : 161);
        }

        // Fall back to (or supplement with) ping sweep + local ARP table
        if (discovered.isEmpty()) {
            log.info("Falling back to ping sweep for {}", subnet);
            for (DiscoveredHost host : scanner.scanSubnet(subnet)) {
                discovered.putIfAbsent(host.getIp(), host.getMac());
            }
        }

        log.info("Subnet scan found {} hosts on {}", discovered.size(), subnet);

        // Resolve hostnames in parallel (each attempt can take up to 4 s)
        Map<String, String> resolved = new ConcurrentHashMap<>();
        ExecutorService resolvePool = Executors.newFixedThreadPool(30);
        for (String ip : discovered.keySet()) {
            if (ip.equals(gateway.getIp())) continue;
            resolvePool.submit(() -> {
                String h = scanner.resolveHostname(ip);
                if (h != null) resolved.put(ip, h);
            });
        }
        resolvePool.shutdown();
        try { resolvePool.awaitTermination(15, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        log.info("Hostname resolution complete — {} of {} resolved", resolved.size(), discovered.size());

        int newCount = 0;
        for (Map.Entry<String, String> entry : discovered.entrySet()) {
            String ip  = entry.getKey();
            String mac = entry.getValue();

            // Skip the gateway itself
            if (ip.equals(gateway.getIp())) continue;

            // Skip devices already tracked by any IP
            boolean alreadyKnown = store.allDevices().stream()
                    .anyMatch(d -> d.getIp().equals(ip));
            if (alreadyKnown) continue;

            String hostname = resolved.getOrDefault(ip, ip);

            Device newDevice = Device.builder()
                    .id(store.nextDeviceId(DeviceType.HOST))
                    .ip(ip)
                    .mac(mac)
                    .hostname(hostname)
                    .vendor("Unknown")
                    .type(DeviceType.HOST)
                    .status(DeviceStatus.NEW)
                    .latencies(new ArrayList<>())
                    .bandwidth(0)
                    .threatLevel(ThreatLevel.none)
                    .firstSeen(LocalTime.now().format(TIME_FMT))
                    .gatewayId(gateway.getId())
                    .build();

            store.putDevice(newDevice);

            Link link = Link.builder()
                    .id(store.nextLinkId())
                    .source(gateway.getId())
                    .target(newDevice.getId())
                    .discoveredBy(DiscoveryMethod.ARP)
                    .build();
            store.putLink(link);

            // Kick off security scan asynchronously (port scan, CVE lookup, cred test)
            securityScanService.scanAsync(newDevice);

            store.addAlert(Alert.builder()
                    .id(store.nextAlertId())
                    .type(AlertType.DEVICE_JOINED)
                    .deviceId(newDevice.getId())
                    .message(ip + " discovered on " + subnet)
                    .timestamp(LocalTime.now().format(TIME_FMT))
                    .build());

            newCount++;
        }

        if (newCount > 0) {
            log.info("Registered {} new devices under gateway {}", newCount, gateway.getHostname());
            broadcaster.broadcastFullState();
        } else {
            log.info("No new devices found on {}", subnet);
        }
    }

    /**
     * Derives the subnet from the gateway's stored subnet field, or infers
     * a /24 from its IP address (e.g. 192.168.1.1 → 192.168.1.0/24).
     */
    private String resolveSubnet(Device gateway) {
        if (gateway.getSubnet() != null && !gateway.getSubnet().isBlank()) {
            return gateway.getSubnet();
        }
        String ip = gateway.getIp();
        if (ip == null) return null;
        String[] parts = ip.split("\\.");
        if (parts.length < 3) return null;
        return parts[0] + "." + parts[1] + "." + parts[2] + ".0/24";
    }
}
