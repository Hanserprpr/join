package cn.sduonline.join.data.po;

import cn.sduonline.join.data.enums.QuestionType;
import lombok.Data;

@Data
public class DepartmentQuestion {
    private Long id;
    private Long departmentId;
    private String title;
    private String description;
    private QuestionType type;
    private Boolean required;
    private Integer sortOrder;
}
