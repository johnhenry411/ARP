package com.topologymapper.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CredentialFinding {
    private int     port;
    private String  service;     // HTTP, SSH, FTP, Telnet, Redis…
    private String  username;
    private String  password;
    private boolean vulnerable;  // true = login succeeded / no auth required
    private String  note;        // human-readable detail
}
