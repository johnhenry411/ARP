package com.topologymapper.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface LinkRepository extends JpaRepository<LinkEntity, String> {

    @Modifying
    @Transactional
    @Query("DELETE FROM LinkEntity l WHERE l.source = :deviceId OR l.target = :deviceId")
    void deleteByDeviceId(@Param("deviceId") String deviceId);
}
