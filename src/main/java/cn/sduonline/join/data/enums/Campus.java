package cn.sduonline.join.data.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 山东大学校区
 */
public enum Campus {

    SOFTWARE_PARK("软件园校区"),
    CENTRAL("中心校区"),
    QIANFOSHAN("千佛山校区"),
    XINGLONGSHAN("兴隆山校区"),
    HONGJIALOU("洪家楼校区"),
    BAOTUQUAN("趵突泉校区");

    private final String displayName;

    Campus(String displayName) {
        this.displayName = displayName;
    }


    /**
     * 根据枚举编码或中文名称解析校区
     *
     * @param value 枚举编码或中文名称
     * @return 对应校区，输入为 null 时返回 null
     * @throws IllegalArgumentException 校区值不受支持
     */
    @JsonCreator
    public static Campus fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (Campus campus : values()) {
            if (campus.name().equalsIgnoreCase(value)
                    || campus.displayName.equals(value)) {
                return campus;
            }
        }
        throw new IllegalArgumentException("不支持的校区：" + value);
    }
}
