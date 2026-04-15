package com.lanprojects.fitcoach.common.config.repository;

import com.lanprojects.fitcoach.common.config.entity.SysConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SysConfigRepository extends JpaRepository<SysConfig, Long> {

    Optional<SysConfig> findByConfigKey(String configKey);

    List<SysConfig> findByConfigGroup(String configGroup);

    List<SysConfig> findByEnabledTrue();
}
