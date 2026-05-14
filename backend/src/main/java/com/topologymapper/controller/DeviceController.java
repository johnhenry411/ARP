package com.topologymapper.controller;

import com.topologymapper.dto.AddDeviceRequest;
import com.topologymapper.model.Alert;
import com.topologymapper.model.Device;
import com.topologymapper.model.DeviceType;
import com.topologymapper.service.SecurityScanService;
import com.topologymapper.service.SubnetScanService;
import com.topologymapper.service.TopologyService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    @Autowired private TopologyService topologyService;
    @Autowired private SubnetScanService subnetScanService;
    @Autowired private SecurityScanService securityScanService;

    @GetMapping
    public Collection<Device> getAll() {
        return topologyService.getAllDevices();
    }

    @GetMapping("/{id}")
    public Device getById(@PathVariable String id) {
        return topologyService.getDevice(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Device addDevice(@RequestBody @Valid AddDeviceRequest req) {
        return topologyService.addDevice(req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeDevice(@PathVariable String id) {
        topologyService.removeDevice(id);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearAllDevices() {
        topologyService.clearAllDevices();
    }

    @PatchMapping("/{id}/rename")
    public Device renameDevice(@PathVariable String id, @RequestBody Map<String, String> body) {
        return topologyService.renameDevice(id, body.get("hostname"));
    }

    @PostMapping("/scan")
    public java.util.Map<String, String> triggerScan() {
        topologyService.triggerManualScan();
        return java.util.Map.of("status", "scan triggered");
    }

    @PostMapping("/{id}/scan")
    public java.util.Map<String, String> scanGateway(@PathVariable String id) {
        Device device = topologyService.getDevice(id);
        if (!device.isGateway() && device.getType() != DeviceType.ROUTER) {
            return java.util.Map.of("status", "skipped", "reason", "device is not a gateway");
        }
        subnetScanService.scanGatewaySubnet(device);
        return java.util.Map.of("status", "subnet scan started for " + device.getHostname());
    }

    @PostMapping("/{id}/security-scan")
    public java.util.Map<String, String> triggerSecurityScan(@PathVariable String id) {
        Device device = topologyService.getDevice(id);
        securityScanService.scanAsync(device);
        return java.util.Map.of("status", "security scan started for " + device.getHostname());
    }

    @GetMapping("/alerts")
    public List<Alert> getAlerts(@RequestParam(defaultValue = "20") int limit) {
        return topologyService.getRecentAlerts(limit);
    }
}
