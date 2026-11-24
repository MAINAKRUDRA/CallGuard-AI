package com.mainak.callguardai.screening

/**
 * Pure, deterministic call-rule evaluation. Keeping this separate from Android APIs
 * makes the safety-critical precedence rules straightforward to unit test.
 */
object CallRuleEvaluator {
    fun evaluate(
        number: String?,
        allowedNumbers: Set<String>,
        blockedNumbers: Set<String>
    ): ScreeningDecision {
        if (number.isNullOrBlank()) {
            return ScreeningDecision(
                ScreeningAction.ALLOW,
                "Caller ID unavailable or invalid; allowed by safe default"
            )
        }
        if (number in allowedNumbers) {
            return ScreeningDecision(ScreeningAction.ALLOW, "Explicit allow rule", "allow")
        }
        if (number in blockedNumbers) {
            return ScreeningDecision(ScreeningAction.REJECT, "Explicit user block rule", "block")
        }
        return ScreeningDecision(
            ScreeningAction.ALLOW,
            "No matching rule; allowed by safe default"
        )
    }
}
