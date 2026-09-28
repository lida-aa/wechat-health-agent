package com.Myself.demo.mapper;

import com.Myself.demo.entity.HealthRecordDocument;
import org.apache.ibatis.annotations.Param;

public interface HealthRecordMapper {

    HealthRecordDocument findByUserId(@Param("userId") String userId);

    int insert(HealthRecordDocument document);

    int updateDocument(
            @Param("userId") String userId,
            @Param("documentJson") String documentJson);
}
