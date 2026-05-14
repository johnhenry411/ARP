package com.topologymapper.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert {
    private String id;
    private AlertType type;
    private String deviceId;
    private String message;
    private String timestamp;
}
