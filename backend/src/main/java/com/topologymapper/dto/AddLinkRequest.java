package com.topologymapper.dto;

import com.topologymapper.model.DiscoveryMethod;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddLinkRequest {
    @NotBlank
    private String source;
    @NotBlank
    private String target;
    private DiscoveryMethod discoveredBy = DiscoveryMethod.ARP;
}
