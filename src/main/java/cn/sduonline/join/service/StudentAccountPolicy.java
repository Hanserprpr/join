package cn.sduonline.join.service;

/** 学生统一认证账号的业务限制。 */
public final class StudentAccountPolicy {

    /** 非主修账号在用户界面中统一展示的提示。 */
    public static final String PRIMARY_ACCOUNT_REQUIRED_NAME = "请使用主修账号进入";

    private StudentAccountPolicy() {
    }

    /**
     * 账号中包含英文字母时，视为不允许维护个人资料的非主修账号。
     */
    public static boolean requiresPrimaryAccount(String casId) {
        if (casId == null) {
            return false;
        }
        return casId.chars().anyMatch(character ->
                (character >= 'A' && character <= 'Z')
                        || (character >= 'a' && character <= 'z'));
    }
}
