package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.sduonline.join.data.enums.Campus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CollegeMajorServiceTest {

    @Test
    void loadsPackagedFallback() {
        CollegeMajorService service = new CollegeMajorService();

        assertTrue(service.isValidCollege("软件学院"));
        assertTrue(service.isValidCollegeMajor("软件学院", "软件工程"));
    }

    @Test
    void replacesSnapshotFromNacosJson() {
        CollegeMajorService service = new CollegeMajorService();

        service.replaceFromJson("""
                {
                  "测试学院": ["测试专业", "第二专业"]
                }
                """);

        assertEquals(Map.of("测试学院", List.of(
                "测试专业", "第二专业"
        )), service.getCollegeMajors());
        assertTrue(service.isValidCollegeMajor("测试学院", "测试专业"));
        assertFalse(service.isValidCollege("软件学院"));
    }

    @Test
    void invalidUpdateKeepsPreviousSnapshot() {
        CollegeMajorService service = new CollegeMajorService();
        Map<String, List<String>> before = service.getCollegeMajors();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.replaceFromJson("{\"测试学院\": []}")
        );

        assertEquals(before, service.getCollegeMajors());
    }

    @Test
    void publishedSnapshotCannotBeMutatedByCallers() {
        CollegeMajorService service = new CollegeMajorService();

        assertThrows(
                UnsupportedOperationException.class,
                () -> service.getCollegeMajors().put("测试学院", List.of())
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> service.getCollegeMajors().values().iterator().next().add("测试专业")
        );
    }

    @Test
    void fallbackResolvesCampusByCollege() {
        CollegeMajorService service = new CollegeMajorService();

        assertEquals(Campus.SOFTWARE_PARK, service.resolveCampus("软件学院"));
        assertEquals(Campus.BAOTUQUAN, service.resolveCampus("药学院"));
        assertNull(service.resolveCampus("不存在学院"));
        assertNull(service.resolveCampus(null));
        // 青岛校区没有枚举取值，学院可以正常填报，只是推不出校区
        assertTrue(service.isValidCollegeMajor("计算机科学与技术学院", "计算机科学与技术"));
        assertNull(service.resolveCampus("计算机科学与技术学院"));
    }

    /** 校区键配置展平后，接口返回与学院键写法完全一致，只是学院按校区分组。 */
    @Test
    void campusKeyedSnapshotFlattensToTheSameResponse() {
        CollegeMajorService service = new CollegeMajorService();

        service.replaceFromJson("""
                {
                  "中心校区": {
                    "文学院": ["汉语言文学"]
                  },
                  "青岛校区": {
                    "法学院": ["法学"]
                  }
                }
                """);

        assertEquals(
                Map.of("文学院", List.of("汉语言文学"), "法学院", List.of("法学")),
                service.getCollegeMajors()
        );
        assertEquals(
                List.of("文学院", "法学院"),
                new ArrayList<>(service.getCollegeMajors().keySet())
        );
        assertEquals(Campus.CENTRAL, service.resolveCampus("文学院"));
        // 济南以外的校区照常出现在列表里，只是推不出校区
        assertNull(service.resolveCampus("法学院"));
        assertTrue(service.isValidCollegeMajor("法学院", "法学"));
    }

    /** 老格式继续可解析，只是学院没有校区归属，配置可以随时回滚。 */
    @Test
    void collegeKeyedSnapshotKeepsWorkingWithoutCampus() {
        CollegeMajorService service = new CollegeMajorService();

        service.replaceFromJson("""
                {
                  "文学院": ["汉语言文学"]
                }
                """);

        assertEquals(Map.of("文学院", List.of("汉语言文学")), service.getCollegeMajors());
        assertNull(service.resolveCampus("文学院"));
    }

    @Test
    void unknownCampusKeepsPreviousSnapshot() {
        CollegeMajorService service = new CollegeMajorService();
        Map<String, List<String>> before = service.getCollegeMajors();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.replaceFromJson(
                        "{\"火星校区\": {\"文学院\": [\"汉语言文学\"]}}")
        );

        assertEquals(before, service.getCollegeMajors());
        assertEquals(Campus.SOFTWARE_PARK, service.resolveCampus("软件学院"));
    }

    /** 同名学院分属两个校区时无法确定学生归属，整份配置必须被拒。 */
    @Test
    void collegeInTwoCampusesKeepsPreviousSnapshot() {
        CollegeMajorService service = new CollegeMajorService();
        Map<String, List<String>> before = service.getCollegeMajors();

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> service.replaceFromJson("""
                        {
                          "洪家楼校区": { "艺术学院": ["美术学"] },
                          "中心校区": { "艺术学院": ["艺术学"] }
                        }
                        """)
        );

        assertTrue(thrown.getMessage().contains("艺术学院"));
        assertTrue(thrown.getMessage().contains("中心校区"));
        assertEquals(before, service.getCollegeMajors());
    }

    /** 济南以外的校区是白名单，拼错的校区名不会被当成“没有校区”放过。 */
    @Test
    void weihaiIsAcceptedButUnknownCampusIsNot() {
        CollegeMajorService service = new CollegeMajorService();

        service.replaceFromJson("""
                {
                  "威海校区": { "海洋学院": ["海洋科学"] }
                }
                """);
        assertTrue(service.isValidCollegeMajor("海洋学院", "海洋科学"));
        assertNull(service.resolveCampus("海洋学院"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.replaceFromJson(
                        "{\"威海分校\": {\"海洋学院\": [\"海洋科学\"]}}")
        );
        assertTrue(service.isValidCollege("海洋学院"));
    }

    @Test
    void emptyCampusGroupKeepsPreviousSnapshot() {
        CollegeMajorService service = new CollegeMajorService();
        Map<String, List<String>> before = service.getCollegeMajors();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.replaceFromJson("{\"中心校区\": {}}")
        );

        assertEquals(before, service.getCollegeMajors());
    }
}
