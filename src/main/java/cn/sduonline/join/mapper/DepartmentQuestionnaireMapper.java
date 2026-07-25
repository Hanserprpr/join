package cn.sduonline.join.mapper;

import cn.sduonline.join.data.po.DepartmentQuestion;
import cn.sduonline.join.data.po.DepartmentQuestionOption;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DepartmentQuestionnaireMapper {

    @Select("""
            SELECT COUNT(1)
            FROM department_question
            WHERE department_id = #{departmentId}
            """)
    long countQuestions(@Param("departmentId") Long departmentId);

    @Select("""
            SELECT id, department_id, title, description, type, required, sort_order
            FROM department_question
            WHERE department_id = #{departmentId}
            ORDER BY sort_order ASC, id ASC
            """)
    List<DepartmentQuestion> selectQuestions(@Param("departmentId") Long departmentId);

    @Select("""
            SELECT id, question_id, content, sort_order
            FROM department_question_option
            WHERE question_id = #{questionId}
            ORDER BY sort_order ASC, id ASC
            """)
    List<DepartmentQuestionOption> selectOptions(@Param("questionId") Long questionId);

    @Insert("""
            INSERT INTO department_question
                (department_id, title, description, type, required, sort_order)
            VALUES
                (#{departmentId}, #{title}, #{description}, #{type}, #{required}, #{sortOrder})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertQuestion(DepartmentQuestion question);

    @Insert("""
            INSERT INTO department_question_option (question_id, content, sort_order)
            VALUES (#{questionId}, #{content}, #{sortOrder})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertOption(DepartmentQuestionOption option);

    @Delete("DELETE FROM department_question WHERE department_id = #{departmentId}")
    int deleteQuestions(@Param("departmentId") Long departmentId);
}
