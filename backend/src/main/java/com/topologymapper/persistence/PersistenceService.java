package com.topologymapper.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.topologymapper.model.*;
import com.topologymapper.model.SecurityScanResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class PersistenceService {

    @Autowired private DeviceRepository deviceRepo;
    @Autowired private LinkRepository   linkRepo;
    @Autowired private ObjectMapper     objectMapper;

    private static final TypeReference<List<Integer>>    INT_LIST = new TypeReference<>() {};
    private static final TypeReference<SecurityScanResult> SCAN_TYPE = new TypeReference<>() {};

    // --- Device ---

    public void saveDevice(Device d) {
        try { deviceRepo.save(toEntity(d)); }
        catch (Exception e) { log.warn("Failed to persist device {}: {}", d.getId(), e.getMessage()); }
    }

    public void deleteDevice(String id) {
        try { deviceRepo.deleteById(id); }
        catch (Exception e) { log.warn("Failed to delete device {}: {}", id, e.getMessage()); }
    }

    // --- Link ---

    public void saveLink(Link l) {
        try { linkRepo.save(toEntity(l)); }
        catch (Exception e) { log.warn("Failed to persist link {}: {}", l.getId(), e.getMessage()); }
    }

    public void deleteLink(String id) {
        try { linkRepo.deleteById(id); }
        catch (Exception e) { log.warn("Failed to delete link {}: {}", id, e.getMessage()); }
    }

    public void deleteLinksForDevice(String deviceId) {
        try { linkRepo.deleteByDeviceId(deviceId); }
        catch (Exception e) { log.warn("Failed to delete links for {}: {}", deviceId, e.getMessage()); }
    }

    public void clearAll() {
        try { deviceRepo.deleteAll(); linkRepo.deleteAll(); }
        catch (Exception e) { log.warn("Failed to clear DB: {}", e.getMessage()); }
    }

    // --- Load on startup ---

    public record LoadResult(List<Device> devices, List<Link> links) {}

    public LoadResult loadAll() {
        List<Device> devices = deviceRepo.findAll().stream().map(this::fromEntity).toList();
        List<Link>   links   = linkRepo.findAll().stream().map(this::fromEntity).toList();
        log.info("Loaded {} devices and {} links from database", devices.size(), links.size());
        return new LoadResult(devices, links);
    }

    // --- Conversion ---

    private DeviceEntity toEntity(Device d) {
        return DeviceEntity.builder()
                .id(d.getId()).ip(d.getIp()).mac(d.getMac()).hostname(d.getHostname())
                .vendor(d.getVendor()).gatewayId(d.getGatewayId()).subnet(d.getSubnet())
                .snmpCommunity(d.getSnmpCommunity()).firstSeen(d.getFirstSeen())
                .type(d.getType()).status(d.getStatus()).threatLevel(d.getThreatLevel())
                .threatReason(d.getThreatReason())
                .bandwidth(d.getBandwidth()).snmpPort(d.getSnmpPort()).isGateway(d.isGateway())
                .latenciesJson(toJson(d.getLatencies()))
                .bandwidthHistoryJson(toJson(d.getBandwidthHistory()))
                .securityScanJson(toJsonObj(d.getSecurityScan()))
                .build();
    }

    private Device fromEntity(DeviceEntity e) {
        return Device.builder()
                .id(e.getId()).ip(e.getIp()).mac(e.getMac()).hostname(e.getHostname())
                .vendor(e.getVendor()).gatewayId(e.getGatewayId()).subnet(e.getSubnet())
                .snmpCommunity(e.getSnmpCommunity()).firstSeen(e.getFirstSeen())
                .type(e.getType()).status(e.getStatus()).threatLevel(e.getThreatLevel())
                .threatReason(e.getThreatReason())
                .bandwidth(e.getBandwidth()).snmpPort(e.getSnmpPort()).isGateway(e.isGateway())
                .latencies(fromJson(e.getLatenciesJson()))
                .bandwidthHistory(fromJson(e.getBandwidthHistoryJson()))
                .securityScan(fromJsonObj(e.getSecurityScanJson()))
                .build();
    }

    private LinkEntity toEntity(Link l) {
        return LinkEntity.builder()
                .id(l.getId()).source(l.getSource()).target(l.getTarget())
                .discoveredBy(l.getDiscoveredBy())
                .build();
    }

    private Link fromEntity(LinkEntity e) {
        return Link.builder()
                .id(e.getId()).source(e.getSource()).target(e.getTarget())
                .discoveredBy(e.getDiscoveredBy())
                .build();
    }

    private String toJson(List<Integer> list) {
        if (list == null || list.isEmpty()) return "[]";
        try { return objectMapper.writeValueAsString(list); }
        catch (Exception e) { return "[]"; }
    }

    private List<Integer> fromJson(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try { return objectMapper.readValue(json, INT_LIST); }
        catch (Exception e) { return new ArrayList<>(); }
    }

    private String toJsonObj(Object obj) {
        if (obj == null) return null;
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return null; }
    }

    private SecurityScanResult fromJsonObj(String json) {
        if (json == null || json.isBlank()) return null;
        try { return objectMapper.readValue(json, SCAN_TYPE); }
        catch (Exception e) { return null; }
    }
}
