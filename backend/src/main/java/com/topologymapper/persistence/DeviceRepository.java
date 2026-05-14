package com.topologymapper.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<DeviceEntity, String> {}
