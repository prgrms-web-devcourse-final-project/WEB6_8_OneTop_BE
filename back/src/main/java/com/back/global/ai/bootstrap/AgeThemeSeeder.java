/*
 * [코드 흐름 요약]
 * - ensureSeedForAgeAsync: 쓰로틀/중복 작업 가드 후 비동기 제출(기존 유지).
 * - doSeed: 필요량 산출 → 대량 후보 생성(템플릿 조합) → 임베딩 배치 → DB 청크 저장.
 * - 10만건 목표를 위해 배치 임베딩(256), 저장 청크(500), 텍스트 중복 방지(Set) 적용.
 */
package com.back.global.ai.bootstrap;

import com.back.domain.node.entity.NodeCategory;
import com.back.domain.search.entity.AgeTheme;
import com.back.domain.search.repository.AgeThemeRepository;
import com.back.global.ai.vector.EmbeddingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgeThemeSeeder {

    private final AgeThemeRepository repo;
    private final EmbeddingClient embedding;

    private final Set<Integer> inProgress = ConcurrentHashMap.newKeySet();
    private final ExecutorService exec = Executors.newFixedThreadPool(2); // next 노드 생성

    // 무결성 검증
    private final ConcurrentHashMap<Integer, Long> lastCheckMs = new ConcurrentHashMap<>();
    private final long THROTTLE_MS = 10 * 60 * 1000L; // 10분으로 조정(중복 경쟁 감소)

    // 무결성 검증
    private static final int EMBED_BATCH = 256;
    private static final int SAVE_BATCH = 500;
    private static final int MAX_GEN_MULTIPLIER = 4; // 필요량 대비 후보 오버샘플
    private static final int MAX_NEW_PER_AGE = 1000;
    // next 노드 생성
    public void ensureSeedForAgeAsync(int age, int minPerCategory) {
        long now = System.currentTimeMillis();
        long prev = lastCheckMs.getOrDefault(age, 0L);
        if (now - prev < THROTTLE_MS) {
            log.debug("[SEEDER] throttle skip age={} remainMs={}", age, (THROTTLE_MS - (now - prev)));
            return;
        }
        lastCheckMs.put(age, now);

        long have = repo.countByMinAge(age);
        long needThreshold = (long) minPerCategory * NodeCategory.values().length;
        if (have >= needThreshold) {
            log.debug("[SEEDER] enough data age={} have={} need>={}", age, have, needThreshold);
            return;
        }
        if (!inProgress.add(age)) {
            log.debug("[SEEDER] already in progress age={}", age);
            return;
        }

        log.info("[SEEDER] submit task age={} minPerCategory={} have={} need>={}", age, minPerCategory, have, needThreshold);
        exec.submit(() -> {
            try {
                doSeed(age, minPerCategory);
                log.info("[SEEDER] done age={}", age);
            } catch (Exception e) {
                log.error("[SEEDER] failed age={} msg={}", age, e.getMessage(), e);
            } finally {
                inProgress.remove(age);
            }
        });
    }

    // 무결성 검증
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void doSeed(int age, int minPerCategory) {
        int insertedTotal = 0;
        for (NodeCategory cat : NodeCategory.values()) {
            if (insertedTotal >= MAX_NEW_PER_AGE) break;
            int have = Math.toIntExact(repo.countByMinAgeAndCategory(age, cat));
            int need = Math.max(0, minPerCategory - have);
            if (need == 0) {
                log.debug("[SEEDER] category skip age={} category={} have>={}", age, cat, minPerCategory);
                continue;
            }

            Set<String> existing = new HashSet<>(repo.findThemesByMinAgeAndCategory(age, cat));
            List<String> acts  = new ArrayList<>(actions(cat));
            List<String> hints = new ArrayList<>(hintsForAge(age));
            if (acts.isEmpty() || hints.isEmpty()) {
                log.warn("[SEEDER] no template age={} category={} acts={} hints={}", age, cat, acts.size(), hints.size());
                continue;
            }

            // next 노드 생성
            List<String> candidates = generateCandidates(age, cat, hints, acts, need * MAX_GEN_MULTIPLIER);
            if (candidates.isEmpty()) continue;

            // 무결성 검증
            List<String> uniques = new ArrayList<>(need);
            for (String c : candidates) {
                String t = tidy(c);
                if (existing.add(t)) uniques.add(t);
                if (uniques.size() >= need) break;
            }
            if (uniques.isEmpty()) {
                log.debug("[SEEDER] no unique candidates age={} category={}", age, cat);
                continue;
            }

            // next 노드 생성
            List<float[]> vectors = embedBatch(uniques);

            List<AgeTheme> buffer = new ArrayList<>(SAVE_BATCH);
            for (int i = 0; i < uniques.size(); i++) {
                if (insertedTotal >= MAX_NEW_PER_AGE) break; // 무결성 검증
                buffer.add(AgeTheme.builder()
                        .minAge(age).maxAge(age).category(cat)
                        .theme(uniques.get(i)).embedding(vectors.get(i))
                        .build());
                if (buffer.size() == SAVE_BATCH || i == uniques.size() - 1) {
                    repo.saveAll(buffer); // next 노드 생성
                    insertedTotal += buffer.size();
                    buffer.clear();
                }
            }
            log.info("[SEEDER] saved age={} category={} added={} total={}",
                    age, cat, Math.min(need, MAX_NEW_PER_AGE - insertedTotal + need), insertedTotal);
        }
    }

    // 무결성 검증
    private List<float[]> embedBatch(List<String> texts) {
        List<float[]> out = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += EMBED_BATCH) {
            int to = Math.min(texts.size(), i + EMBED_BATCH);
            List<String> sub = texts.subList(i, to);
            try {
                out.addAll(embedding.embedBatch(sub));
            } catch (Throwable t) {
                for (String s : sub) out.add(embedding.embed(s));
            }
        }
        return out;
    }

    // 무결성 검증
    private String tidy(String s) { return s.replaceAll("\\s+", " ").trim(); }

    // next 노드 생성
    private List<String> generateCandidates(
            int age, NodeCategory cat,
            List<String> hints, List<String> acts,
            int max
    ) {
        // 무결성 검증: 불변 리스트 -> 가변 리스트로 복사
        List<String> H = new ArrayList<>(hints);
        List<String> A = new ArrayList<>(acts);
        List<String> actors  = new ArrayList<>(actors(cat));
        List<String> objects = new ArrayList<>(objects(cat));
        List<String> mods    = new ArrayList<>(modifiers());
        List<String> times   = new ArrayList<>(timePhrases());
        List<String> places  = new ArrayList<>(placePhrases(cat));

        // 무결성 검증
        if (H.isEmpty() || A.isEmpty() || actors.isEmpty() || objects.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> templates = List.of(
                "%s | %s %s %s",
                "%s | %s %s %s (%s)",
                "%s | %s %s %s - %s",
                "%s | %s %s %s @%s",
                "%s | %s가 %s %s (%s @%s)"
        );

        // 무결성 검증: 결정적 셔플(재현성)
        Random r = new Random(31_557_600L + age * 131 + cat.ordinal() * 17);
        Collections.shuffle(H, r);
        Collections.shuffle(A, r);
        Collections.shuffle(actors, r);
        Collections.shuffle(objects, r);
        Collections.shuffle(mods, r);
        Collections.shuffle(times, r);
        Collections.shuffle(places, r);

        // next 노드 생성
        List<String> out = new ArrayList<>(max);
        for (int i = 0; i < max; i++) {
            String t = templates.get(i % templates.size());
            String s = switch (t) {
                case "%s | %s %s %s" ->
                        String.format(t, H.get(i % H.size()), actors.get(i % actors.size()), A.get(i % A.size()), objects.get(i % objects.size()));
                case "%s | %s %s %s (%s)" ->
                        String.format(t, H.get(i % H.size()), actors.get(i % actors.size()), A.get(i % A.size()), objects.get(i % objects.size()), times.get(i % times.size()));
                case "%s | %s %s %s - %s" ->
                        String.format(t, H.get(i % H.size()), actors.get(i % actors.size()), A.get(i % A.size()), objects.get(i % objects.size()), mods.get(i % mods.size()));
                case "%s | %s %s %s @%s" ->
                        String.format(t, H.get(i % H.size()), actors.get(i % actors.size()), A.get(i % A.size()), objects.get(i % objects.size()), places.get(i % places.size()));
                default ->
                        String.format(t, H.get(i % H.size()), actors.get(i % actors.size()), A.get(i % A.size()), objects.get(i % objects.size()), times.get(i % times.size()), places.get(i % places.size()));
            };
            out.add(s);
        }
        return out;
    }

    // next 노드 생성
    private List<String> actions(NodeCategory cat) {
        return switch (cat) {
            case EDUCATION -> List.of("과제 정리","강의 복습","프로젝트 준비","발표 연습","스터디 합류","요약 노트",
                    "모의고사 분석","멘토 질의","레포트 초안","탐구 설계","팀 프로젝트 조율","피드백 반영");
            case CAREER -> List.of("이력서 업데이트","포트폴리오 정리","인턴 지원","멘토 미팅","면접 준비","기업 분석",
                    "직무 과제 연습","링크드인 정비","사내 세미나 참가","자격증 학습","직무 독서","해커톤 준비");
            case RELATIONSHIP -> List.of("팀 협업","피드백 교환","네트워킹","동아리 활동","행사 운영","면담 준비",
                    "협업 회의","갈등 중재","튜터링","스터디 운영","커뮤니티 참여","봉사 연계");
            case FINANCE -> List.of("예산 점검","지출 기록","지원금 탐색","저축 계획","보험 점검","가계부 정리",
                    "투자 포트폴리오 점검","세금 공제 확인","비상금 적립","구독 정리","비용 최적화","장학금 신청");
            case HEALTH -> List.of("수면 루틴","운동 습관","식단 관리","스트레칭","정기 검진","휴식 스케줄",
                    "명상 루틴","보행 기록","물 섭취 체크","자세 교정","체력 측정","건강 일지");
            case LOCATION -> List.of("동선 최적화","캠퍼스 맵 확인","시설 파악","교통패스 점검","학기 동선 계획","스터디카페 탐색",
                    "통학 시간 계산","근처 병원 파악","헬스장 등록","서점/도서관 루트","코워킹스페이스 탐색","야간 귀가 경로");
            case ETC -> List.of("시간 관리","디지털 정리","기록 루틴","취미 프로젝트","주간 회고","도구 템플릿 정비",
                    "자동화 스크립트","알림 규칙 정비","백업 점검","버전 관리","목표 설정","루틴 최적화");
        };
    }

    // 무결성 검증
    private List<String> hintsForAge(int age) {
        if (age <= 7)   return List.of("기초 루틴","놀이 중심","정서 안정","생활 습관","부모 참여","기초 체력");
        if (age <= 13)  return List.of("중간·기말","수행평가","학습 습관","독서 루틴","기초 코딩","기초 수학");
        if (age <= 16)  return List.of("고입 준비","내신 관리","진로 탐색","동아리 심화","실험 보고","탐구 기록");
        if (age <= 19)  return List.of("모의고사","수능 대비","자기소개서","학생부 보강","로드맵 설계","컨디션 관리");
        if (age == 20)  return List.of("전공 기초","OT/동아리","캠퍼스 리소스","교양 확장","멘토 찾기","학사 시스템");
        if (age <= 23)  return List.of("전공 심화","현장실습","포트폴리오","학회/세미나","프로젝트 리딩","자격 준비");
        if (age <= 29)  return List.of("인턴/첫 직장","직무 역량","멘토링","사내 적응","사이드 프로젝트","업계 네트워킹");
        if (age <= 39)  return List.of("전문성","리더십","성과 관리","영향력 확대","스킬 매트릭스","OKR/성과지표");
        if (age <= 49)  return List.of("코칭","팀 빌딩","로드맵","조직 협업","프로세스 개선","후배 양성");
        if (age <= 59)  return List.of("세컨 커리어","건강/재정","은퇴 준비","가족 계획","지역 커뮤니티","지식 기여");
        if (age <= 69)  return List.of("퇴직 설계","커뮤니티","건강 루틴","소일거리","자원봉사","여가 기획");
        if (age <= 79)  return List.of("사회공헌","관계 유지","여가 설계","건강 모니터링","경험 공유","멘토 역할");
        if (age <= 89)  return List.of("생활 단순화","회상","가족 소통","취미 심화","건강 관리","지역 서비스");
        return List.of("돌봄 연계","일상 기능","의료 연계","지역 지원망","생활 안정","정서적 교류");
    }

    // 무결성 검증
    private List<String> actors(NodeCategory cat) {
        return switch (cat) {
            case EDUCATION -> List.of("나","팀원","멘토","조교","교수","튜터");
            case CAREER -> List.of("나","동료","리쿠루터","팀 리드","멘토","코치");
            case RELATIONSHIP -> List.of("나","친구","동아리원","파트너","동료","커뮤니티");
            case FINANCE -> List.of("나","가족","설계사","상담사","회계사","은행원");
            case HEALTH -> List.of("나","트레이너","의사","치료사","동호회","코치");
            case LOCATION -> List.of("나","동행자","안내센터","시설 담당","사서","매니저");
            case ETC -> List.of("나","동료","운영진","관리자","봇","시스템");
        };
    }

    // 무결성 검증
    private List<String> objects(NodeCategory cat) {
        return switch (cat) {
            case EDUCATION -> List.of("자료","발표","보고서","레포트","실험","과제");
            case CAREER -> List.of("이력서","포트폴리오","직무과제","커버레터","평판요청","과제제출");
            case RELATIONSHIP -> List.of("회의안건","협업툴","피드백노트","행사기획","네트워킹리스트","연락처");
            case FINANCE -> List.of("예산표","가계부","보험내역","투자현황","납부일정","청구서");
            case HEALTH -> List.of("식단표","운동계획","검진표","수면기록","활동량","케어플랜");
            case LOCATION -> List.of("이동경로","시설목록","정기권","주차정보","좌석배치","출입권한");
            case ETC -> List.of("템플릿","백업","자동화","버전노트","태스크보드","알림규칙");
        };
    }

    // 무결성 검증
    private List<String> modifiers() {
        return List.of("신속히","정확히","가볍게","심화로","체계적으로","꾸준히","집중해서","효율적으로","간결하게","확장해서");
    }

    // 무결성 검증
    private List<String> timePhrases() {
        return List.of("오늘","이번 주","다음 주","월초","월말","분기 내","주말","야간","아침 루틴","점심 시간");
    }

    // 무결성 검증
    private List<String> placePhrases(NodeCategory cat) {
        return switch (cat) {
            case EDUCATION -> List.of("도서관","공학관","스터디룸","랩실","온라인");
            case CAREER -> List.of("사무실","회의실","온보딩룸","온라인","코워킹스페이스");
            case RELATIONSHIP -> List.of("동아리방","라운지","행사장","카페","온라인");
            case FINANCE -> List.of("은행","가정","상담센터","앱","온라인");
            case HEALTH -> List.of("헬스장","공원","병원","재활센터","집");
            case LOCATION -> List.of("캠퍼스","지하철","버스터미널","주차장","도서관");
            case ETC -> List.of("집","작업실","서버룸","클라우드","온라인");
        };
    }
}
