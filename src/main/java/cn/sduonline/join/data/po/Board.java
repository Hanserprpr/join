package cn.sduonline.join.data.po;

import lombok.Data;

@Data
public class Board {

    private Long id;
    private String name;
    private Integer sortOrder;
    private Boolean enabled;
}
