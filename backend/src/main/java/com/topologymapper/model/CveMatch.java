package com.topologymapper.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CveMatch {
    private String cveId;        // e.g. CVE-2021-44228
    private double cvssScore;    // 0.0 – 10.0
    private String severity;     // CRITICAL / HIGH / MEDIUM / LOW
    private String description;  // one-line summary
    private String affectedPort; // e.g. "22 (SSH)"
}
