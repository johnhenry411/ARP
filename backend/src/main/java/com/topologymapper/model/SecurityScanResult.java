package com.topologymapper.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SecurityScanResult {
    private String timestamp;
    /** CRITICAL / HIGH / MEDIUM / LOW / CLEAN */
    private String riskLevel;
    private List<OpenPort>          openPorts;
    private List<CveMatch>          cveMatches;
    private List<CredentialFinding> credentialFindings;
    private List<String>            arpAnomalies;

    public int getCveCount()      { return cveMatches          == null ? 0 : cveMatches.size(); }
    public int getOpenPortCount() { return openPorts            == null ? 0 : openPorts.size(); }
    public int getVulnCount()     { return credentialFindings   == null ? 0
            : (int) credentialFindings.stream().filter(CredentialFinding::isVulnerable).count(); }
}
