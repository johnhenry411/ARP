package com.topologymapper.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PacketSimulateRequest {
    @NotBlank
    private String sourceId;
    @NotBlank
    private String targetId;
}
