package com.Myself.demo.repository;

import com.Myself.demo.entity.HealthRecordDocument;
import com.Myself.demo.mapper.HealthRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MySqlHealthRecordRepositoryTest {

    @Test
    void databaseRecordIsCanonicalAndSkipsLegacyFile() {
        HealthRecordMapper mapper = mock(HealthRecordMapper.class);
        LegacyHealthRecordReader legacy = mock(LegacyHealthRecordReader.class);
        HealthRecordDocument stored = new HealthRecordDocument();
        stored.setUserId("user-a");
        stored.setDocumentJson("{\"profile\":{\"age\":30}}");
        when(mapper.findByUserId("user-a")).thenReturn(stored);

        Map<String, Object> result = repository(mapper, legacy).load("user-a");

        assertThat(result).containsKey("profile");
        verify(legacy, never()).read(anyString());
    }

    @Test
    void importsLegacyRecordWhenDatabaseHasNoRecord() {
        HealthRecordMapper mapper = mock(HealthRecordMapper.class);
        LegacyHealthRecordReader legacy = mock(LegacyHealthRecordReader.class);
        when(mapper.findByUserId("user-a")).thenReturn(null);
        when(legacy.read("user-a")).thenReturn(Optional.of(Map.of("profile", Map.of("age", 30))));
        when(mapper.updateDocument(anyString(), anyString())).thenReturn(0);
        when(mapper.insert(any(HealthRecordDocument.class))).thenReturn(1);

        Map<String, Object> result = repository(mapper, legacy).load("user-a");

        assertThat(result).containsKey("profile");
        verify(mapper).insert(any(HealthRecordDocument.class));
    }

    @Test
    void concurrentFirstWriteFallsBackToUpdate() {
        HealthRecordMapper mapper = mock(HealthRecordMapper.class);
        LegacyHealthRecordReader legacy = mock(LegacyHealthRecordReader.class);
        when(mapper.updateDocument("user-a", "{\"value\":1}"))
                .thenReturn(0, 1);
        when(mapper.insert(any(HealthRecordDocument.class)))
                .thenThrow(new DuplicateKeyException("concurrent insert"));

        repository(mapper, legacy).save("user-a", Map.of("value", 1));

        verify(mapper).insert(any(HealthRecordDocument.class));
        verify(mapper, org.mockito.Mockito.times(2))
                .updateDocument("user-a", "{\"value\":1}");
    }

    private MySqlHealthRecordRepository repository(
            HealthRecordMapper mapper, LegacyHealthRecordReader legacy) {
        return new MySqlHealthRecordRepository(mapper, new ObjectMapper(), legacy);
    }
}
