package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.sduonline.join.data.dto.InterviewRoomRequest;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.DepartmentInterviewRoom;
import cn.sduonline.join.data.po.DepartmentInterviewSession;
import cn.sduonline.join.mapper.DepartmentInterviewMapper;
import cn.sduonline.join.mapper.DepartmentInterviewRoomMapper;
import cn.sduonline.join.mapper.DepartmentInterviewSessionMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
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
    void listsOnlyTheRequestedSessionsRooms() {
        when(sessionMapper.selectById(12L, 5L))
                .thenReturn(new DepartmentInterviewSession());
        DepartmentInterviewRoom room = new DepartmentInterviewRoom();
        room.setId(9L);
        room.setDepartmentId(12L);
        room.setSessionId(5L);
        room.setName("第一面试室");
        room.setStatus("OPEN");
        when(roomMapper.selectBySession(12L, 5L)).thenReturn(List.of(room));

        var result = service.findBySession(12L, 5L);

        assertTrue(result.isSuccess());
        assertEquals(1, result.data().size());
        assertEquals(5L, result.data().getFirst().sessionId());
    }

    @Test
    void returnsBusinessErrorForDuplicateRoomNameWithoutJoiningOrPublishing() {
        when(sessionMapper.selectPublishedForUpdate(12L, 5L))
                .thenReturn(new DepartmentInterviewSession());
        when(roomMapper.insert(any()))
                .thenThrow(new DuplicateKeyException("duplicate room name"));
        when(roomMapper.selectOpenIdBySessionAndNameForUpdate(5L, "第一面试室"))
                .thenReturn(9L);

        var result = service.create(
                12L, "admin01", new InterviewRoomRequest(5L, " 第一面试室 ")
        );

        assertEquals(BizCode.INTERVIEW_ROOM_NAME_EXISTS, result.error());
        verify(roomMapper, never()).insertMember(any(), any());
        verifyNoInteractions(interviewSseService);
    }

    @Test
    void propagatesDuplicateKeyWhenNoRoomHasTheSameSessionAndName() {
        when(sessionMapper.selectPublishedForUpdate(12L, 5L))
                .thenReturn(new DepartmentInterviewSession());
        var failure = new DuplicateKeyException("unexpected unique constraint");
        when(roomMapper.insert(any())).thenThrow(failure);
        when(roomMapper.selectOpenIdBySessionAndNameForUpdate(5L, "第一面试室"))
                .thenReturn(null);

        var thrown = assertThrows(DuplicateKeyException.class, () -> service.create(
                12L, "admin01", new InterviewRoomRequest(5L, "第一面试室")
        ));

        assertSame(failure, thrown);
        verify(roomMapper, never()).insertMember(any(), any());
        verifyNoInteractions(interviewSseService);
    }

    @Test
    void propagatesDatabaseFailuresOtherThanDuplicateKeys() {
        when(sessionMapper.selectPublishedForUpdate(12L, 5L))
                .thenReturn(new DepartmentInterviewSession());
        var failure = new DataAccessResourceFailureException("database unavailable");
        when(roomMapper.insert(any())).thenThrow(failure);

        var thrown = assertThrows(DataAccessResourceFailureException.class,
                () -> service.create(
                        12L, "admin01", new InterviewRoomRequest(5L, "第一面试室")
                ));

        assertSame(failure, thrown);
        verify(roomMapper, never()).selectOpenIdBySessionAndNameForUpdate(any(), any());
        verify(roomMapper, never()).insertMember(any(), any());
        verifyNoInteractions(interviewSseService);
    }

    @Test
    void rejectsListingRoomsOfASessionOutsideTheDepartment() {
        when(sessionMapper.selectById(12L, 5L)).thenReturn(null);

        var result = service.findBySession(12L, 5L);

        assertEquals(BizCode.INTERVIEW_SESSION_NOT_FOUND, result.error());
        verify(roomMapper, never()).selectBySession(any(), any());
    }

    @Test
    void joiningIgnoresSessionStatusSoEndedSessionsCanFinishTheirQueue() {
        DepartmentInterviewRoom room = new DepartmentInterviewRoom();
        room.setId(9L);
        room.setDepartmentId(12L);
        room.setSessionId(5L);
        room.setStatus("OPEN");
        when(roomMapper.selectByIdForUpdate(12L, 9L)).thenReturn(room);

        var result = service.join(12L, 9L, "admin02");

        // 场次结束只关签到，面试室仍要能进去把排队的人面完。
        assertTrue(result.isSuccess());
        verify(roomMapper).insertMember(9L, "admin02");
        verify(sessionMapper, never()).selectPublishedById(any(), any());
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
