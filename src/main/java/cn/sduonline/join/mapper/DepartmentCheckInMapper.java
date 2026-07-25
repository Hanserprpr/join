package cn.sduonline.join.mapper;

import cn.sduonline.join.data.po.DepartmentCheckIn;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DepartmentCheckInMapper {

    @Select("""
            SELECT id, department_id, application_id, cas_id,
                   checked_in_at, queue_number, queue_order, pass_count
            FROM department_check_in
            WHERE application_id = #{applicationId}
            """)
    DepartmentCheckIn selectByApplicationId(
            @Param("applicationId") Long applicationId
    );

    @Insert("""
            INSERT INTO department_check_in
                (department_id, application_id, cas_id, checked_in_at,
                 queue_number, queue_order, pass_count)
            VALUES
                (#{departmentId}, #{applicationId}, #{casId}, #{checkedInAt},
                 #{queueNumber}, #{queueOrder}, #{passCount})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DepartmentCheckIn checkIn);

    @Insert("""
            INSERT IGNORE INTO department_check_in_sequence
                (department_id, next_number)
            VALUES (#{departmentId}, 1)
            """)
    int initializeSequence(@Param("departmentId") Long departmentId);

    @Select("""
            SELECT next_number
            FROM department_check_in_sequence
            WHERE department_id = #{departmentId}
            FOR UPDATE
            """)
    int selectNextNumberForUpdate(@Param("departmentId") Long departmentId);

    @Update("""
            UPDATE department_check_in_sequence
            SET next_number = next_number + 1
            WHERE department_id = #{departmentId}
            """)
    int incrementNextNumber(@Param("departmentId") Long departmentId);
}
