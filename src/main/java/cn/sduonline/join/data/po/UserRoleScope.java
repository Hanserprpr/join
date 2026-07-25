package cn.sduonline.join.data.po;

import lombok.Data;

@Data
public class UserRoleScope {

    private Long id;
    private String casId;
    private Long roleId;
    private String scopeType;
    private Long scopeId;
}
