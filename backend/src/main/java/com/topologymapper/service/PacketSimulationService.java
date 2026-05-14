package com.topologymapper.service;

import com.topologymapper.model.Device;
import com.topologymapper.model.Link;
import com.topologymapper.model.PacketTrace;
import com.topologymapper.store.TopologyStore;
import com.topologymapper.websocket.WebSocketBroadcaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PacketSimulationService {

    @Autowired private TopologyStore store;
    @Autowired private WebSocketBroadcaster broadcaster;

    private static final int PACKETS_SENT = 10;
    private static final int HOP_DELAY_MS = 300;
    private static final Random random = new Random();

    @Async
    public void simulate(String traceId, String sourceId, String targetId) {
        log.debug("Starting packet simulation {} → {}", sourceId, targetId);

        List<String> path = bfsPath(sourceId, targetId);

        if (path == null) {
            broadcaster.broadcastRaw(PacketTrace.unreachable(traceId, sourceId, targetId));
            return;
        }

        List<Integer> hopLatencies = computeHopLatencies(path);

        // Emit a PACKET_HOP event for each hop with a delay between them
        for (int i = 0; i < path.size() - 1; i++) {
            try { Thread.sleep(HOP_DELAY_MS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }

            String fromId = path.get(i);
            String toId = path.get(i + 1);
            int latency = hopLatencies.size() > i ? hopLatencies.get(i) : 999;

            broadcaster.broadcastRaw(Map.of(
                    "type", "PACKET_HOP",
                    "traceId", traceId,
                    "fromId", fromId,
                    "toId", toId,
                    "hopIndex", i + 1,
                    "latencyMs", latency
            ));
        }

        // Calculate loss: 2% per hop, compounded
        double successRate = Math.pow(0.98, path.size() - 1);
        int lost = (int) Math.round(PACKETS_SENT * (1.0 - successRate));
        lost = Math.min(PACKETS_SENT, Math.max(0, lost + random.nextInt(3) - 1));
        int received = PACKETS_SENT - lost;

        double avgLatency = hopLatencies.stream().mapToInt(Integer::intValue).average().orElse(0);

        List<String> hopHostnames = path.stream()
                .map(id -> store.getDevice(id).map(Device::getHostname).orElse(id))
                .collect(Collectors.toList());

        PacketTrace result = PacketTrace.builder()
                .traceId(traceId)
                .sourceId(sourceId)
                .targetId(targetId)
                .hopIds(path)
                .hopHostnames(hopHostnames)
                .totalHops(path.size() - 1)
                .avgLatencyMs(Math.round(avgLatency * 10.0) / 10.0)
                .packetLossPercent(Math.round((double) lost / PACKETS_SENT * 1000.0) / 10.0)
                .packetsSent(PACKETS_SENT)
                .packetsReceived(received)
                .hopLatenciesMs(hopLatencies)
                .status("SUCCESS")
                .build();

        broadcaster.broadcastRaw(result);
        log.debug("Simulation complete: traceId={}, hops={}, loss={}%", traceId, result.getTotalHops(), result.getPacketLossPercent());
    }

    private List<String> bfsPath(String sourceId, String targetId) {
        if (sourceId.equals(targetId)) return List.of(sourceId);

        Map<String, List<String>> adj = buildAdjacency();
        Map<String, String> parent = new HashMap<>();
        Queue<String> queue = new ArrayDeque<>();

        queue.add(sourceId);
        parent.put(sourceId, null);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String neighbor : adj.getOrDefault(current, List.of())) {
                if (!parent.containsKey(neighbor)) {
                    parent.put(neighbor, current);
                    if (neighbor.equals(targetId)) return reconstructPath(parent, targetId);
                    queue.add(neighbor);
                }
            }
        }
        return null;
    }

    private Map<String, List<String>> buildAdjacency() {
        Map<String, List<String>> adj = new HashMap<>();
        for (Link link : store.allLinks()) {
            adj.computeIfAbsent(link.getSource(), k -> new ArrayList<>()).add(link.getTarget());
            adj.computeIfAbsent(link.getTarget(), k -> new ArrayList<>()).add(link.getSource());
        }
        return adj;
    }

    private List<String> reconstructPath(Map<String, String> parent, String target) {
        LinkedList<String> path = new LinkedList<>();
        String cur = target;
        while (cur != null) {
            path.addFirst(cur);
            cur = parent.get(cur);
        }
        return path;
    }

    private List<Integer> computeHopLatencies(List<String> path) {
        return path.stream().skip(1).map(id ->
                store.getDevice(id).map(d -> {
                    List<Integer> lat = d.getLatencies();
                    if (lat.isEmpty()) return 999;
                    return lat.stream().mapToInt(Integer::intValue).sum() / lat.size();
                }).orElse(999)
        ).collect(Collectors.toList());
    }
}
