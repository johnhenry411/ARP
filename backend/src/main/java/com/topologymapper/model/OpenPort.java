package com.topologymapper.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OpenPort {
    private int    port;
    private String service;  // "SSH", "HTTP", …
    private String banner;   // raw service banner (first line)
    private String version;  // parsed version string if detected
}
