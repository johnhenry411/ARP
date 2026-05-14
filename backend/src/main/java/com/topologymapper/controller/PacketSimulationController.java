package com.topologymapper.controller;

import com.topologymapper.dto.PacketSimulateRequest;
import com.topologymapper.service.PacketSimulationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/simulate")
public class PacketSimulationController {

    @Autowired
    private PacketSimulationService packetSimulationService;

    @PostMapping("/packet")
    public Map<String, String> simulatePacket(@RequestBody @Valid PacketSimulateRequest req) {
        String traceId = UUID.randomUUID().toString();
        packetSimulationService.simulate(traceId, req.getSourceId(), req.getTargetId());
        return Map.of("traceId", traceId, "status", "simulation started");
    }
}
