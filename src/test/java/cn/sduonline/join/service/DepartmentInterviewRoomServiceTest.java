package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.sduonline.join.data.dto.InterviewRoomRequest;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.DepartmentInterviewRoom;
import cn.sduonline.join.data.po.DepartmentInterviewSession;
import cn.sduonline.join.mapper.DepartmentInterviewMapper;
import cn.sduonline.join.mapper.DepartmentInterviewRoomMapper;
import cn.sduonline.join.mapper.DepartmentInterviewSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class DepartmentInterviewRoomServiceTest {

    @Mock DepartmentInterviewRoomMapper roomMapper;
    @Mock DepartmentInterviewSessionMapper sessionMapper;
    @Mock DepartmentInterviewMapper interviewMapper;
    @Mock TransactionTemplate transactionTemplate;
    @Mock InterviewSseService interviewSseService;
    private DepartmentInterviewRoomService service;

    @BeforeEach
    void setUp() {
        service = new DepartmentInterviewRoomService(
                roomMapper, sessionMapper, interviewMapper,
                transactionTemplate, interviewSseService
        );
    }

    @Test
    void createsRoomOnlyAfterLockingPublishedSession() {
        DepartmentInterviewSession session = new DepartmentInterviewSession();
        session.setId(5L);
        when(sessionMapper.selectPublishedForUpdate(12L, 5L))
                .thenReturn(session);
        doAnswer(invocation -> {
            DepartmentInterviewRoom room = invocation.getArgument(0);
            room.setId(9L);
            return 1;
        }).when(roomMapper).insert(any());

        var result = service.create(
                12L, "admin01", new InterviewRoomRequest(5L, " 第一面试室 ")
        );

        assertTrue(result.isSuccess());
        assertEquals(5L, result.data().sessionId());
        assertEquals("第一面试室", result.data().name());
        verify(roomMapper).insertMember(9L, "admin01");
    }

    @Test
    void rejectsRoomForDraftOrEndedSession() {
        DepartmentInterviewSession session = new DepartmentInterviewSession();
        session.setId(5L);
        when(sessionMapper.selectPublishedForUpdate(12L, 5L))
                .thenReturn(null);
        when(sessionMapper.selectById(12L, 5L)).thenReturn(session);

        var result = service.create(
                12L, "admin01", new InterviewRoomRequest(5L, "第一面试室")
        );

        assertEquals(BizCode.INTERVIEW_SESSION_STATE_INVALID, result.error());
        verify(roomMapper, never()).insert(any());
    }
}
