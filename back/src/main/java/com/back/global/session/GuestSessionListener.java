package com.back.global.session;

import com.back.domain.node.entity.BaseLine;
import com.back.domain.node.repository.BaseLineRepository;
import com.back.domain.node.service.BaseLineService;
import com.back.domain.user.entity.Role;
import com.back.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 게스트 세션이 만료되거나 삭제될 때 해당 게스트 계정과 관련된 모든 데이터를 정리하는 리스너
 * (Spring Session의 SessionExpiredEvent / SessionDeletedEvent를 수신)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuestSessionListener {

    private final UserRepository userRepository;
    private final BaseLineRepository baseLineRepository;
    private final BaseLineService baseLineService;

    @EventListener({SessionExpiredEvent.class, SessionDeletedEvent.class})
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSessionEvent(AbstractSessionEvent event) {
        final Session session = event.getSession();
        if (session == null) return;

        final String principalName =
                session.getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);
        if (principalName == null || !principalName.startsWith("guest_")) return;

        var userOpt = userRepository.findByEmail(principalName)
                .filter(u -> u.getRole() == Role.GUEST);

        if (userOpt.isEmpty()) {
            log.info("[GuestSessionListener] 이미 삭제되었거나 게스트 아님: {}", principalName);
            return;
        }

        var user = userOpt.get();
        Long userId = user.getId();

        try {
            // 게스트 소유 baseline 조회
            List<Long> baseLineIds = baseLineRepository.findByUser_IdOrderByIdDesc(userId).stream().map(BaseLine::getId).toList();

            // deleteBaseLineDeep -> baseLine부터 baseNode, decisionLine, scenario 등등 모두 다 삭제
            for (Long baseLineId : baseLineIds) {
                try {
                    baseLineService.deleteBaseLineDeep(userId, baseLineId);
                } catch (Exception ex) {
                    log.error("[GuestSessionListener] baseline({}) 삭제 실패 - 계속 진행", baseLineId, ex);
                }
            }

            userRepository.delete(user);

            log.info("[GuestSessionListener] {} -> 게스트 및 소유 리소스 삭제 완료: {}",
                    (event instanceof SessionExpiredEvent) ? "SessionExpiredEvent" : "SessionDeletedEvent",
                    principalName);

        } catch (Exception e) {
            log.error("[GuestSessionListener] 게스트({}) 삭제 처리 중 오류", principalName, e);
            throw e;
        }
    }
}
