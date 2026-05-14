package com.topologymapper.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PacketTrace {
    private String traceId;
    private String sourceId;
    private String targetId;
    private List<String> hopIds;
    private List<String> hopHostnames;
    private int totalHops;
    private double avgLatencyMs;
    private double packetLossPercent;
    private int packetsSent;
    private int packetsReceived;
    private List<Integer> hopLatenciesMs;
    private String status;

    public static PacketTrace unreachable(String traceId, String sourceId, String targetId) {
        return PacketTrace.builder()
                .traceId(traceId)
                .sourceId(sourceId)
                .targetId(targetId)
                .hopIds(List.of())
                .hopHostnames(List.of())
                .totalHops(0)
                .avgLatencyMs(0)
                .packetLossPercent(100)
                .packetsSent(10)
                .packetsReceived(0)
                .hopLatenciesMs(List.of())
                .status("UNREACHABLE")
                .build();
    }
}
