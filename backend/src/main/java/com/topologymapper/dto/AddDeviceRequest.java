package com.topologymapper.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.topologymapper.model.DeviceType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddDeviceRequest {
    @NotBlank
    private String ip;
    @NotBlank
    private String mac;
    @NotBlank
    private String hostname;
    private String vendor = "Unknown";
    private DeviceType type = DeviceType.HOST;
    @JsonProperty("isGateway")
    private boolean isGateway = false;
    private String gatewayId;
    private int bandwidth = 100;
    private String subnet;         // e.g. "192.168.1.0/24" — auto-inferred if omitted for gateways
    private String snmpCommunity;  // null = no SNMP; set to "public" to enable
    private int snmpPort = 161;
}
