package com.topologymapper.persistence;

import com.topologymapper.model.DiscoveryMethod;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "links")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LinkEntity {
    @Id
    private String id;
    private String source;
    private String target;

    @Enumerated(EnumType.STRING)
    private DiscoveryMethod discoveredBy;
}
