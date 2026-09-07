package cn.sduonline.join.data.dto;

/** 基础用户库提供的学院、专业，仅用于首次 OIDC 登录预填。 */
public record StudentAcademicProfile(String college, String major) {
}
