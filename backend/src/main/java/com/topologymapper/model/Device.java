package com.topologymapper.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Device {
    private String id;
    private String ip;
    private String mac;
    private String hostname;
    private String vendor;
    private DeviceType type;
    private DeviceStatus status;
    @Builder.Default
    private List<Integer> latencies = new ArrayList<>();
    private int bandwidth;
    private ThreatLevel threatLevel;
    private String threatReason;
    private String firstSeen;
    @JsonProperty("isGateway")
    private boolean isGateway;
    private String gatewayId;
    // Network scanning config
    private String subnet;          // e.g. "192.168.1.0/24" — used for gateway ARP sweep
    private String snmpCommunity;   // null = SNMP disabled; "public" is the common default
    @Builder.Default
    private int snmpPort = 161;
    @Builder.Default
    private List<Integer> bandwidthHistory = new ArrayList<>();
    private SecurityScanResult securityScan;
}
