package cn.sduonline.join.data.po;

import lombok.Data;

/**
 * 部门成果实体
 */
@Data
public class DepartmentAchievement {

    private Long id;
    private Long departmentId;
    private String title;
    private String content;
    private Integer sortOrder;
}
