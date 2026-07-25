package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.enums.QuestionType;
import java.util.List;

public record ApplicationAnswerVO(
        Long questionId,
        String questionTitle,
        QuestionType questionType,
        String answerText,
        List<ApplicationAnswerOptionVO> selectedOptions
) {
}
