package com.topologymapper.service;

import com.topologymapper.dto.AddDeviceRequest;
import com.topologymapper.dto.AddLinkRequest;
import com.topologymapper.model.*;
import com.topologymapper.store.TopologyStore;
import com.topologymapper.websocket.WebSocketBroadcaster;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class TopologyService {

    @Autowired private TopologyStore store;
    @Autowired private WebSocketBroadcaster broadcaster;
    @Autowired private PingScheduler pingScheduler;
    @Autowired private SubnetScanService subnetScanService;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // --- Devices ---

    public Collection<Device> getAllDevices() {
        return store.allDevices();
    }

    public Device getDevice(String id) {
        return store.getDevice(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found: " + id));
    }

    public Device addDevice(AddDeviceRequest req) {
        DeviceType type = req.getType() != null ? req.getType() : DeviceType.HOST;

        boolean isGateway = req.isGateway() || type == DeviceType.ROUTER;

        Device device = Device.builder()
                .id(store.nextDeviceId(type))
                .ip(req.getIp())
                .mac(req.getMac())
                .hostname(req.getHostname())
                .vendor(req.getVendor() != null ? req.getVendor() : "Unknown")
                .type(type)
                .status(DeviceStatus.NEW)
                .latencies(new ArrayList<>())
                .bandwidth(req.getBandwidth() > 0 ? req.getBandwidth() : 0)
                .threatLevel(ThreatLevel.none)
                .firstSeen(LocalTime.now().format(TIME_FMT))
                .isGateway(isGateway)
                .gatewayId(req.getGatewayId())
                .subnet(req.getSubnet())
                .snmpCommunity(req.getSnmpCommunity())
                .snmpPort(req.getSnmpPort() > 0 ? req.getSnmpPort() : 161)
                .build();

        store.putDevice(device);

        if (req.getGatewayId() != null && store.containsDevice(req.getGatewayId())) {
            Link link = Link.builder()
                    .id(store.nextLinkId())
                    .source(req.getGatewayId())
                    .target(device.getId())
                    .discoveredBy(DiscoveryMethod.ARP)
                    .build();
            store.putLink(link);
        }

        store.addAlert(Alert.builder()
                .id(store.nextAlertId())
                .type(AlertType.DEVICE_JOINED)
                .deviceId(device.getId())
                .message(device.getHostname() + " joined")
                .timestamp(LocalTime.now().format(TIME_FMT))
                .build());

        broadcaster.broadcastFullState();

        // If this is a gateway, kick off an async subnet scan to discover connected devices
        if (isGateway) {
            subnetScanService.scanGatewaySubnet(device);
        }

        return device;
    }

    public void clearAllDevices() {
        store.clearAll();
        broadcaster.broadcastFullState();
    }

    public Device renameDevice(String id, String hostname) {
        if (hostname == null || hostname.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hostname cannot be blank");
        Device device = getDevice(id);
        device.setHostname(hostname.trim());
        store.putDevice(device);
        broadcaster.broadcastFullState();
        return device;
    }

    public void removeDevice(String id) {
        if (!store.containsDevice(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found: " + id);
        }
        String hostname = store.getDevice(id).map(Device::getHostname).orElse(id);
        store.removeDevice(id);
        store.removeLinksForDevice(id);

        store.addAlert(Alert.builder()
                .id(store.nextAlertId())
                .type(AlertType.DEVICE_LOST)
                .deviceId(id)
                .message(hostname + " removed")
                .timestamp(LocalTime.now().format(TIME_FMT))
                .build());

        broadcaster.broadcastFullState();
    }

    // --- Links ---

    public Collection<Link> getAllLinks() {
        return store.allLinks();
    }

    public Link addLink(AddLinkRequest req) {
        if (!store.containsDevice(req.getSource())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source device not found: " + req.getSource());
        }
        if (!store.containsDevice(req.getTarget())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target device not found: " + req.getTarget());
        }
        Link link = Link.builder()
                .id(store.nextLinkId())
                .source(req.getSource())
                .target(req.getTarget())
                .discoveredBy(req.getDiscoveredBy() != null ? req.getDiscoveredBy() : DiscoveryMethod.ARP)
                .build();
        store.putLink(link);
        broadcaster.broadcastFullState();
        return link;
    }

    public void removeLink(String id) {
        if (!store.removeLinkById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Link not found: " + id);
        }
        broadcaster.broadcastFullState();
    }

    // --- Scan ---

    public void triggerManualScan() {
        store.addAlert(Alert.builder()
                .id(store.nextAlertId())
                .type(AlertType.SCAN_STARTED)
                .message("Manual scan triggered")
                .timestamp(LocalTime.now().format(TIME_FMT))
                .build());
        pingScheduler.runScanCycleNow();
    }

    public List<Alert> getRecentAlerts(int limit) {
        return store.recentAlerts(limit);
    }
}
