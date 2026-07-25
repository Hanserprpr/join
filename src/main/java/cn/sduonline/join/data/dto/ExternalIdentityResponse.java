package cn.sduonline.join.data.dto;

/**
 * 外部身份接口响应。
 */
public record ExternalIdentityResponse(
        Integer code,
        String message,
        ExternalUser data
) {
    public record ExternalUser(
            String casId,
            String name,
            String depart,
            String major
    ) {
        public ExternalStudentIdentity toStudentIdentity() {
            return new ExternalStudentIdentity(casId, name, depart, major);
        }
    }
}
