package cn.sduonline.join.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class MapperSqlContractTest {

    @Test
    void interviewLookupMapsRoomIdUsedByAuthorizationChecks()
            throws NoSuchMethodException {
        Method method = DepartmentInterviewMapper.class.getMethod(
                "selectByIdAndDepartment", Long.class, Long.class
        );

        assertTrue(sql(method.getAnnotation(Select.class).value())
                .contains("i.room_id"));
    }

    @Test
    void queueClaimIsScopedToTheRoomsSession()
            throws NoSuchMethodException {
        Method method = DepartmentInterviewMapper.class.getMethod(
                "selectNextWaitingForUpdate", Long.class, Long.class
        );
        String sql = sql(method.getAnnotation(Select.class).value());

        assertTrue(sql.contains("c.session_id = #{sessionId}"));
        assertTrue(sql.contains("FOR UPDATE SKIP LOCKED"));
    }

    @Test
    void admissionPublishingLocksAndUpdatesTheSameIdentifiers()
            throws NoSuchMethodException {
        Method mutate = DepartmentApplicationMapper.class.getMethod(
                "selectByDepartmentAndIdForUpdate", Long.class, Long.class
        );
        Method select = DepartmentApplicationMapper.class.getMethod(
                "selectAdmissionDraftsForUpdate", Long.class
        );
        Method update = DepartmentApplicationMapper.class.getMethod(
                "publishAdmissionDraftsByIds", Long.class, List.class
        );

        assertTrue(sql(mutate.getAnnotation(Select.class).value())
                .contains("FOR UPDATE"));
        assertTrue(sql(select.getAnnotation(Select.class).value())
                .contains("FOR UPDATE"));
        assertTrue(sql(update.getAnnotation(Update.class).value())
                .contains("collection=\"applicationIds\""));
    }

    private static String sql(String[] fragments) {
        return String.join("\n", fragments);
    }
}
