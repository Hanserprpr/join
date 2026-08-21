package cn.sduonline.join.data.po;

import cn.sduonline.join.data.enums.Campus;
import lombok.Data;

@Data
public class Department {

    private Long id;
    private Long workstationId;
    private String name;
    private Campus campus;
    private Long assetId;
    private String introduction;
    private String recruitmentRequirements;
    private String contact;
    private String recruitmentGroup;
    private Integer sortOrder;
    private Boolean enabled;
}
