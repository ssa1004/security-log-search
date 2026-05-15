package com.example.security.domain.common

/**
 * 심각도. ECS 의 [event.severity] (0~100) 와 [log.level] 양쪽을 고려한 5단계 분류.
 *
 * ECS 표준은 0~100 정수지만 운영 관점에서는 5단계로 묶어서 보는 것이 알람 대응 / 대시보드에
 * 적합하다. 본 도메인에서는 5단계를 1차 모델로 두고 ECS 변환 시 매핑한다.
 */
enum class Severity(private val ecsScore: Int) {
    /** 정보성 (event.severity 0~19). */
    INFO(10),

    /** 낮음 (20~39). */
    LOW(30),

    /** 중간 (40~59) — 모니터링 대시보드 등장. */
    MEDIUM(50),

    /** 높음 (60~79) — 알람 발생, on-call 확인. */
    HIGH(70),

    /** 매우 높음 (80~100) — 즉시 대응. */
    CRITICAL(90),
    ;

    fun ecsScore(): Int = ecsScore

    companion object {
        @JvmStatic
        fun fromEcsScore(score: Int): Severity {
            require(score in 0..100) { "ECS event.severity 는 0~100: $score" }
            return when {
                score < 20 -> INFO
                score < 40 -> LOW
                score < 60 -> MEDIUM
                score < 80 -> HIGH
                else -> CRITICAL
            }
        }
    }
}
