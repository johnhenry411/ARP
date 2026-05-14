package com.topologymapper.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Link {
    private String id;
    private String source;
    private String target;
    private DiscoveryMethod discoveredBy;
}
