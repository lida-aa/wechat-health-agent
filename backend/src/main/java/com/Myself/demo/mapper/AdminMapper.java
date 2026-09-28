package com.Myself.demo.mapper;

import com.Myself.demo.entity.UserInfo;
import com.Myself.demo.entity.UserMessage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AdminMapper {

    @Select("SELECT * FROM admin_user_info ORDER BY last_msg_time DESC")
    List<UserInfo> listUsers();

    @Insert("INSERT INTO admin_user_info (from_user_id, first_msg_time, last_msg_time, msg_count) "
            + "VALUES (#{fromUserId}, NOW(), NOW(), 1) "
            + "ON DUPLICATE KEY UPDATE last_msg_time = NOW(), msg_count = msg_count + 1")
    void upsertUser(@Param("fromUserId") String fromUserId);

    @Update("UPDATE admin_user_info SET nickname = #{nickname} WHERE from_user_id = #{userId}")
    void updateNickname(@Param("userId") String userId, @Param("nickname") String nickname);

    @Select("SELECT * FROM admin_user_messages WHERE from_user_id = #{userId} "
            + "ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
    List<UserMessage> getMessages(@Param("userId") String userId,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    @Insert("INSERT INTO admin_user_messages (from_user_id, role, content) "
            + "VALUES (#{userId}, #{role}, #{content})")
    void insertMessage(@Param("userId") String userId,
                       @Param("role") String role,
                       @Param("content") String content);

    @Delete("DELETE FROM admin_user_messages WHERE from_user_id = #{userId}")
    void deleteMessages(@Param("userId") String userId);

    @Delete("<script>DELETE FROM admin_user_messages WHERE from_user_id = #{userId} AND id IN "
            + "<foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int deleteMessagesBatch(@Param("userId") String userId, @Param("ids") List<Long> ids);

    @Delete("DELETE FROM admin_user_info WHERE from_user_id = #{userId}")
    void deleteUser(@Param("userId") String userId);

    @Insert("INSERT INTO agent_traces "
            + "(user_id, task_id, round_num, tool_name, tool_args, tool_result, status, retry_count) "
            + "VALUES (#{userId}, #{taskId}, #{round}, #{toolName}, #{toolArgs}, #{toolResult}, #{status}, #{retryCount})")
    void insertTrace(@Param("userId") String userId,
                     @Param("taskId") String taskId,
                     @Param("round") int round,
                     @Param("toolName") String toolName,
                     @Param("toolArgs") String toolArgs,
                     @Param("toolResult") String toolResult,
                     @Param("status") String status,
                     @Param("retryCount") int retryCount);

    @Select("SELECT password FROM admin_user WHERE username = #{username}")
    String getAdminPassword(@Param("username") String username);

    @Insert("INSERT INTO admin_user(username, password) VALUES(#{username}, #{passwordHash}) "
            + "ON DUPLICATE KEY UPDATE password = VALUES(password)")
    void upsertAdmin(@Param("username") String username,
                     @Param("passwordHash") String passwordHash);
}
