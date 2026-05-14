package com.topologymapper.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DnsRule {
    private String  id;
    /** Domain to intercept. "*" means catch-all. Wildcards: "*.henry.com" */
    private String  domain;
    /** IP to return in the spoofed A record — usually the laptop's hotspot IP */
    private String  targetIp;
    /** Human-readable title shown on the captive portal landing page */
    private String  pageTitle;
    /** Body text / HTML snippet shown on the landing page */
    private String  pageBody;
    private boolean enabled;
    private boolean catchAll;
}
