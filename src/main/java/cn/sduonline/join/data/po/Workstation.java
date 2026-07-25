package cn.sduonline.join.data.po;

import lombok.Data;

@Data
public class Workstation {

    private Long id;
    private Long boardId;
    private String name;
    private Integer sortOrder;
    private Boolean enabled;
}
