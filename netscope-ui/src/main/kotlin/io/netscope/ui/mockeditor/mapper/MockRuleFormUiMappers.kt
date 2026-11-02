package io.netscope.ui.mockeditor.mapper

import io.netscope.model.MockRule
import io.netscope.ui.mockeditor.model.MockRuleFormUi
import java.util.UUID

internal fun MockRule.toFormUi() = MockRuleFormUi(
    ruleId      = id,
    name        = name,
    urlPattern  = urlPattern,
    matchType   = matchType,
    method      = method,
    statusCode  = statusCode.toString(),
    contentType = contentType,
    body        = responseBody,
    delayMs     = delayMs.toString(),
    isEnabled   = isEnabled,
)

internal fun MockRuleFormUi.toMockRule() = MockRule(
    id           = ruleId ?: UUID.randomUUID().toString(),
    name         = name.trim(),
    urlPattern   = urlPattern.trim(),
    matchType    = matchType,
    method       = method,
    statusCode   = statusCodeValue ?: DEFAULT_STATUS_CODE,
    contentType  = contentType.trim(),
    responseBody = body,
    delayMs      = delayMs.toLongOrNull() ?: 0L,
    isEnabled    = isEnabled,
)

private const val DEFAULT_STATUS_CODE = 200
