package com.topologymapper.persistence;

import com.topologymapper.model.DeviceStatus;
import com.topologymapper.model.DeviceType;
import com.topologymapper.model.ThreatLevel;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "devices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceEntity {
    @Id
    private String id;
    private String ip;
    private String mac;
    private String hostname;
    private String vendor;
    private String gatewayId;
    private String subnet;
    private String snmpCommunity;
    private String firstSeen;

    @Enumerated(EnumType.STRING)
    private DeviceType type;

    @Enumerated(EnumType.STRING)
    private DeviceStatus status;

    @Enumerated(EnumType.STRING)
    private ThreatLevel threatLevel;

    private String threatReason;
    private int bandwidth;
    private int snmpPort;

    @Column(name = "is_gateway")
    private boolean isGateway;

    @Column(columnDefinition = "TEXT")
    private String latenciesJson;

    @Column(columnDefinition = "TEXT")
    private String bandwidthHistoryJson;

    @Column(columnDefinition = "TEXT")
    private String securityScanJson;
}
