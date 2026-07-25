package cn.sduonline.join.data.po;

import lombok.Data;

/**
 * 部门海报实体
 */
@Data
public class DepartmentPoster {

    private Long id;
    private Long departmentId;
    private String url;
    private Integer sortOrder;
}
