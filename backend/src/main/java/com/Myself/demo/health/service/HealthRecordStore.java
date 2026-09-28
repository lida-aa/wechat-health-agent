package com.Myself.demo.health.service;

import com.Myself.demo.repository.HealthRecordRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;

/** Serializes read-modify-write operations for each user across health modules. */
@Component
public class HealthRecordStore {

    private static final int LOCK_STRIPES = 64;

    private final HealthRecordRepository repository;
    private final Object[] locks = new Object[LOCK_STRIPES];

    public HealthRecordStore(HealthRecordRepository repository) {
        this.repository = repository;
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
    }

    public Map<String, Object> snapshot(String userId) {
        return read(userId, record -> record);
    }

    public <T> T read(String userId, Function<Map<String, Object>, T> reader) {
        synchronized (lockFor(userId)) {
            return reader.apply(repository.load(userId));
        }
    }

    public <T> T update(String userId, Function<Map<String, Object>, T> mutation) {
        synchronized (lockFor(userId)) {
            Map<String, Object> record = repository.load(userId);
            T result = mutation.apply(record);
            repository.save(userId, record);
            return result;
        }
    }

    public void clear(String userId) {
        update(userId, record -> {
            record.clear();
            return null;
        });
    }

    private Object lockFor(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        return locks[Math.floorMod(userId.hashCode(), locks.length)];
    }
}
