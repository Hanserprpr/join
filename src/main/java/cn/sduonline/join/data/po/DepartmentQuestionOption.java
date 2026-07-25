package cn.sduonline.join.data.po;

import lombok.Data;

@Data
public class DepartmentQuestionOption {
    private Long id;
    private Long questionId;
    private String content;
    private Integer sortOrder;
}
