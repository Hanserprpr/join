package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.enums.QuestionType;
import cn.sduonline.join.data.po.DepartmentQuestion;
import java.util.List;

public record DepartmentQuestionVO(
        Long id,
        String title,
        String description,
        QuestionType type,
        Boolean required,
        Integer sortOrder,
        List<QuestionOptionVO> options
) {
    public static DepartmentQuestionVO from(
            DepartmentQuestion question,
            List<QuestionOptionVO> options
    ) {
        return new DepartmentQuestionVO(
                question.getId(), question.getTitle(), question.getDescription(),
                question.getType(), question.getRequired(), question.getSortOrder(),
                options
        );
    }
}
