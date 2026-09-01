package cn.sduonline.join.data.po;

import java.util.List;
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
    private List<String> imageUrls;
    private Integer sortOrder;
}
