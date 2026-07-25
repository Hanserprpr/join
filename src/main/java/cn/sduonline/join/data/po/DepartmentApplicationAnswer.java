package cn.sduonline.join.data.po;

import lombok.Data;
import cn.sduonline.join.data.enums.QuestionType;

@Data
public class DepartmentApplicationAnswer {
    private Long id;
    private Long applicationId;
    private Long questionId;
    private String questionTitle;
    private QuestionType questionType;
    private String answerText;
}
