package com.Myself.demo.health.service;

import com.Myself.demo.support.InMemoryHealthRecordRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class HealthRecordStoreTest {

    @Test
    void serializesWritesFromDifferentHealthModulesForSameUser() throws Exception {
        HealthRecordStore store = new HealthRecordStore(new InMemoryHealthRecordRepository());
        MedicationHealthService medications = new MedicationHealthService(store);
        HealthProfileService profiles = new HealthProfileService(store);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < 20; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    if (index % 2 == 0) {
                        medications.add("user-a", "药品-" + index, "1片", "08:00", 10);
                    } else {
                        profiles.recordFact("user-a", "档案字段-" + index, "值-" + index);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }

        Map<String, Object> record = store.snapshot("user-a");
        assertThat(list(record, "medications")).hasSize(10);
        assertThat(map(record, "profile")).hasSize(11); // 10 facts plus 最后更新
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Map<String, Object> record, String key) {
        return (List<Map<String, Object>>) record.get(key);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Map<String, Object> record, String key) {
        return (Map<String, Object>) record.get(key);
    }
}
