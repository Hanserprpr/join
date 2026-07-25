package cn.sduonline.join.data.dto;

/**
 * 可信外部系统返回的学生身份。
 *
 * @param studentNumber 学号，也是此外部登录方式在本系统中的 loginId
 * @param name 姓名
 * @param college 学院
 * @param major 专业
 */
public record ExternalStudentIdentity(
        String studentNumber,
        String name,
        String college,
        String major
) {
}
