package cn.sduonline.join.service;

import cn.sduonline.join.data.dto.InterviewRoomRequest;
import cn.sduonline.join.data.dto.InterviewRoomVO;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.DepartmentInterviewRoom;
import cn.sduonline.join.data.po.DepartmentInterviewSession;
import cn.sduonline.join.mapper.DepartmentInterviewMapper;
import cn.sduonline.join.mapper.DepartmentInterviewRoomMapper;
import cn.sduonline.join.mapper.DepartmentInterviewSessionMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class DepartmentInterviewRoomService {

    private final DepartmentInterviewRoomMapper roomMapper;
    private final DepartmentInterviewSessionMapper sessionMapper;
    private final DepartmentInterviewMapper interviewMapper;
    private final TransactionTemplate transactionTemplate;
    private final InterviewSseService interviewSseService;

    @Transactional
    public ServiceResult<InterviewRoomVO> create(
            Long departmentId,
            String creatorCasId,
            InterviewRoomRequest request
    ) {
        DepartmentInterviewSession session =
                sessionMapper.selectPublishedForUpdate(
                        departmentId, request.sessionId()
                );
        if (session == null) {
            return sessionMapper.selectById(
                    departmentId, request.sessionId()
            ) == null
                    ? ServiceResult.failure(BizCode.INTERVIEW_SESSION_NOT_FOUND)
                    : ServiceResult.failure(
                            BizCode.INTERVIEW_SESSION_STATE_INVALID
                    );
        }
        DepartmentInterviewRoom room = new DepartmentInterviewRoom();
        room.setDepartmentId(departmentId);
        room.setSessionId(request.sessionId());
        room.setName(request.name().trim());
        room.setCreatedBy(creatorCasId);
        room.setStatus("OPEN");
        try {
            roomMapper.insert(room);
        } catch (DuplicateKeyException exception) {
            // 当前读确认开放中的房间重名；已关闭的房间允许复用名称。
            if (roomMapper.selectOpenIdBySessionAndNameForUpdate(
                    room.getSessionId(), room.getName()
            ) == null) {
                throw exception;
            }
            return ServiceResult.failure(BizCode.INTERVIEW_ROOM_NAME_EXISTS);
        }
        roomMapper.insertMember(room.getId(), creatorCasId);
        interviewSseService.publishRoomAfterCommit(
                departmentId, room.getId()
        );
        return ServiceResult.success(toVO(room));
    }

    /**
     * 列出指定场次的面试室。
     * <p>
     * 面试室属于场次而不是部门，仅开放中的房间名在场次内保持唯一。不按场次过滤时
     * 同名的新旧面试室会并排出现在同一个列表里，面试官很容易进错。
     * 这里不筛状态：场次结束后房间会刻意留着把排队的人面完，
     * 已关闭的房间也仍需要出现在本场次的记录里。
     */
    public ServiceResult<List<InterviewRoomVO>> findBySession(
            Long departmentId, Long sessionId
    ) {
        if (sessionMapper.selectById(departmentId, sessionId) == null) {
            return ServiceResult.failure(BizCode.INTERVIEW_SESSION_NOT_FOUND);
        }
        return ServiceResult.success(
                roomMapper.selectBySession(departmentId, sessionId)
                        .stream().map(this::toVO).toList()
        );
    }

    @Transactional
    public ServiceResult<InterviewRoomVO> join(
            Long departmentId, Long roomId, String casId
    ) {
        DepartmentInterviewRoom room = roomMapper.selectByIdForUpdate(
                departmentId, roomId
        );
        if (room == null) {
            return ServiceResult.failure(BizCode.INTERVIEW_ROOM_NOT_FOUND);
        }
        if (!"OPEN".equals(room.getStatus())) {
            return ServiceResult.failure(BizCode.INTERVIEW_ROOM_CLOSED);
        }
        try {
            roomMapper.insertMember(roomId, casId);
        } catch (DuplicateKeyException ignored) {
            // 加入操作幂等。
        }
        interviewSseService.publishRoomAfterCommit(departmentId, roomId);
        return ServiceResult.success(toVO(room));
    }

    @Transactional
    public ServiceResult<InterviewRoomVO> leave(
            Long departmentId, Long roomId, String casId
    ) {
        DepartmentInterviewRoom room = roomMapper.selectById(
                departmentId, roomId
        );
        if (room == null) {
            return ServiceResult.failure(BizCode.INTERVIEW_ROOM_NOT_FOUND);
        }
        roomMapper.deleteMember(roomId, casId);
        interviewSseService.publishRoomAfterCommit(departmentId, roomId);
        return ServiceResult.success(toVO(room));
    }

    public ServiceResult<InterviewRoomVO> close(
            Long departmentId, Long roomId, String casId
    ) {
        return transactionTemplate.execute(status -> {
            DepartmentInterviewRoom room =
                    roomMapper.selectByIdForUpdate(departmentId, roomId);
            if (room == null) {
                return ServiceResult.failure(BizCode.INTERVIEW_ROOM_NOT_FOUND);
            }
            if (roomMapper.countMember(roomId, casId) == 0) {
                return ServiceResult.failure(BizCode.INTERVIEW_ROOM_NOT_JOINED);
            }
            if (interviewMapper.selectActiveByRoom(roomId) != null) {
                return ServiceResult.failure(BizCode.INTERVIEW_ROOM_BUSY);
            }
            roomMapper.close(departmentId, roomId);
            room.setStatus("CLOSED");
            interviewSseService.publishRoomAfterCommit(departmentId, roomId);
            return ServiceResult.success(toVO(room));
        });
    }

    private InterviewRoomVO toVO(DepartmentInterviewRoom room) {
        return InterviewRoomVO.from(
                room, roomMapper.selectMemberCasIds(room.getId())
        );
    }
}
