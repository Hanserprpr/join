package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertEquals(Map.of("测试学院", java.util.List.of(
                "测试专业", "第二专业"
        )), service.getCollegeMajors());
        assertTrue(service.isValidCollegeMajor("测试学院", "测试专业"));
        assertFalse(service.isValidCollege("软件学院"));
    }

    @Test
    void invalidUpdateKeepsPreviousSnapshot() {
        CollegeMajorService service = new CollegeMajorService();
        Map<String, java.util.List<String>> before = service.getCollegeMajors();

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
                () -> service.getCollegeMajors().put("测试学院", java.util.List.of())
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> service.getCollegeMajors().values().iterator().next().add("测试专业")
        );
    }
}
