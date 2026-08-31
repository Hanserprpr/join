package cn.sduonline.join.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置。
 */
@Configuration
public class OpenApiConfig {

    private static final Map<String, String> OPERATION_SUMMARIES = Map.ofEntries(
            Map.entry("AdminController.createBoard", "创建板块"),
            Map.entry("AdminController.createWorkstation", "创建工作站"),
            Map.entry("AdminController.createDepartment", "创建部门"),
            Map.entry("AdminController.updateBoardName", "修改板块名称"),
            Map.entry("AdminController.updateWorkstationName", "修改工作站名称"),
            Map.entry("AdminController.updateDepartmentName", "修改部门名称"),
            Map.entry("AdminController.deleteBoard", "删除板块"),
            Map.entry("AdminController.deleteWorkstation", "删除工作站"),
            Map.entry("AdminController.deleteDepartment", "删除部门"),
            Map.entry("AdminController.getRoleAssignments", "查询组织成员角色授权"),
            Map.entry("AdminController.searchUsers", "按学号模糊匹配授权用户"),
            Map.entry("AdminController.findUsers", "分页查询平台用户"),
            Map.entry("AdminController.assignRole", "分配角色"),
            Map.entry("AdminController.revokeRole", "撤销角色分配"),
            Map.entry("AuthController.login", "获取 OIDC 登录地址"),
            Map.entry("AuthController.status", "检查登录状态"),
            Map.entry("AuthController.me", "获取当前登录用户资料"),
            Map.entry("AuthController.oidcClaims", "获取当前用户的 OIDC Claims"),
            Map.entry("BannerController.getBanners", "获取轮播图列表"),
            Map.entry("BoardController.getBoards", "获取板块列表"),
            Map.entry("OrganizationController.getOrganizations", "获取公开组织树"),
            Map.entry("CollegeMajorController.getCollegeMajors", "获取学院与专业列表"),
            Map.entry("DepartmentApplicationController.findApplications", "分页查询部门报名信息"),
            Map.entry("DepartmentApplicationController.getApplicationDetail", "获取报名记录详情"),
            Map.entry("DepartmentApplicationController.exportApplications", "导出部门报名信息"),
            Map.entry("DepartmentApplicationController.submit", "提交部门报名"),
            Map.entry("DepartmentCheckInController.createQrCode", "生成部门签到二维码"),
            Map.entry("DepartmentCheckInController.checkIn", "签到"),
            Map.entry("DepartmentController.getDepartment", "获取部门详情"),
            Map.entry("DepartmentController.updateDepartment", "完整更新部门详情"),
            Map.entry("DepartmentController.patchDepartment", "部分更新部门详情"),
            Map.entry("DepartmentController.getQuestionnaire", "获取部门报名问卷"),
            Map.entry("DepartmentController.updateQuestionnaire", "完整更新部门报名问卷"),
            Map.entry("DepartmentController.clearQuestionnaire", "清空部门报名问卷"),
            Map.entry("DepartmentInterviewController.createSession", "创建面试场次"),
            Map.entry("DepartmentInterviewController.updateSession", "修改面试场次"),
            Map.entry("DepartmentInterviewController.publishSession", "发布面试场次"),
            Map.entry("DepartmentInterviewController.endSession", "结束面试场次"),
            Map.entry("DepartmentInterviewController.findPublishedSession", "查询当前已发布场次"),
            Map.entry("DepartmentInterviewController.findSessions", "查询全部面试场次"),
            Map.entry("DepartmentInterviewController.findQueueConfig", "查询过号配置"),
            Map.entry("DepartmentInterviewController.updateQueueConfig", "完整更新过号配置"),
            Map.entry("DepartmentInterviewController.patchQueueConfig", "部分更新过号配置"),
            Map.entry("DepartmentInterviewController.findQueue", "查询面试队列"),
            Map.entry("DepartmentInterviewController.findMyQueueStatus", "查询我的排队状态"),
            Map.entry("DepartmentInterviewController.subscribeQueue", "订阅面试队列事件"),
            Map.entry("DepartmentInterviewController.subscribeMyQueueStatus", "订阅我的排队状态事件"),
            Map.entry("DepartmentInterviewController.findEvaluationsByUserId", "按用户查询面试评价"),
            Map.entry("DepartmentInterviewRoomController.callNext", "面试室呼叫下一位面试者"),
            Map.entry("DepartmentInterviewRoomController.current", "查询面试室当前面试"),
            Map.entry("DepartmentInterviewRoomController.finish", "结束面试室当前面试"),
            Map.entry("DepartmentInterviewRoomController.forceFinish", "强制结束面试室当前面试"),
            Map.entry("DepartmentInterviewRoomController.forceCallNext", "强制结束并呼叫下一位"),
            Map.entry("DepartmentInterviewRoomController.submitEvaluation", "提交管理员独立评价"),
            Map.entry("DepartmentInterviewRoomController.state", "查询面试室实时状态"),
            Map.entry("DepartmentInterviewRoomController.events", "订阅面试室实时状态"),
            Map.entry("DepartmentInterviewRoomController.evaluations", "查询面试的全部管理员评价"),
            Map.entry("DepartmentInterviewController.findById", "查询面试记录"),
            Map.entry("DepartmentInterviewController.updateEvaluation", "更新面试评价"),
            Map.entry("DepartmentInterviewRoomController.pass", "将面试室当前面试者标记为过号"),
            Map.entry("DepartmentInterviewRoomController.stopCalling", "停止面试室叫号"),
            Map.entry("UserApplicationController.findMyApplications", "获取我的全部报名记录"),
            Map.entry("UserProfileController.profile", "获取个人资料"),
            Map.entry("UserProfileController.updateContact", "更新联系方式"),
            Map.entry("WeChatBindingController.authorizationUrl", "获取微信授权绑定地址"),
            Map.entry("WeChatBindingController.callback", "处理微信授权回调"),
            Map.entry("WeChatBindingController.status", "查询微信绑定状态"),
            Map.entry("WeChatBindingController.unbind", "解除微信绑定"),
            Map.entry("WeChatCheckInController.entry", "微信扫码登录并签到"),
            Map.entry("WeChatLoginController.authorizationUrl", "获取微信内登录授权地址"),
            Map.entry("WeChatLoginController.callback", "处理已绑定微信登录回调"),
            Map.entry("WorkstationController.getWorkstation", "获取工作站详情")
    );

    private static final Map<String, String> CONTROLLER_TAGS = Map.ofEntries(
            Map.entry("AdminController", "系统管理"),
            Map.entry("AuthController", "认证"),
            Map.entry("BannerController", "轮播图"),
            Map.entry("BoardController", "板块"),
            Map.entry("CollegeMajorController", "学院专业"),
            Map.entry("DepartmentApplicationController", "部门报名"),
            Map.entry("DepartmentCheckInController", "签到"),
            Map.entry("DepartmentController", "部门"),
            Map.entry("DepartmentInterviewController", "面试"),
            Map.entry("OrganizationController", "组织"),
            Map.entry("UserApplicationController", "部门报名"),
            Map.entry("UserProfileController", "个人资料"),
            Map.entry("WeChatBindingController", "微信绑定"),
            Map.entry("WeChatCheckInController", "微信签到"),
            Map.entry("WeChatLoginController", "微信登录"),
            Map.entry("WorkstationController", "工作站")
    );

    @Bean
    public OpenAPI joinOpenApi() {
        String securitySchemeName = "sessionCookie";
        return new OpenAPI()
                .info(new Info()
                        .title("学生在线纳新系统 API")
                        .description("学生在线纳新系统后端接口")
                        .version("1.0.0"))
                .components(new Components().addSecuritySchemes(
                        securitySchemeName,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JOINSESSION")
                ));
    }

    /**
     * 为接口补充中文名称，并明确标记无需登录的公开接口。
     */
    @Bean
    public OperationCustomizer operationCustomizer() {
        return (operation, handlerMethod) -> {
            String controller = handlerMethod.getBeanType().getSimpleName();
            String key = controller + "." + handlerMethod.getMethod().getName();
            operation.setSummary(OPERATION_SUMMARIES.getOrDefault(key, operation.getSummary()));
            operation.setTags(List.of(CONTROLLER_TAGS.getOrDefault(controller, controller)));

            boolean authenticated =
                    requiresAuthentication(handlerMethod.getBeanType().getAnnotations())
                            || requiresAuthentication(handlerMethod.getMethod().getAnnotations())
                            || "AuthController.me".equals(key);
            if (authenticated) {
                operation.setSecurity(List.of(
                        new SecurityRequirement().addList("sessionCookie")
                ));
            } else {
                operation.setSecurity(null);
            }
            return operation;
        };
    }

    private static boolean requiresAuthentication(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            String name = annotation.annotationType().getSimpleName();
            if ("SaCheckLogin".equals(name)
                    || "SaCheckRole".equals(name)
                    || name.endsWith("Permission")) {
                return true;
            }
        }
        return false;
    }
}
