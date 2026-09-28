package com.Myself.demo.repository;

import java.util.Map;

/** Canonical persistence boundary for a user's complete health record. */
public interface HealthRecordRepository {

    Map<String, Object> load(String userId);

    void save(String userId, Map<String, Object> record);
}
