// [TEST-ONLY] AI 실호출 예산 관리
package com.back.domain.node.controller;

import java.util.concurrent.atomic.AtomicInteger;

public class AiCallBudget {
    private final AtomicInteger budget = new AtomicInteger(0);
    // 한줄 요약: 남은 실호출 횟수를 설정한다
    public void reset(int n) { budget.set(Math.max(0, n)); }
    // 한줄 요약: 남은 실호출이 있으면 1 소진하고 true
    public boolean consume() { return budget.getAndUpdate(x -> x > 0 ? x - 1 : 0) > 0; }
}
