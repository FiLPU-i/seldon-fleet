package br.edu.uea.seldonfleet.ui

import br.edu.uea.seldonfleet.domain.model.ClassificationResult
import br.edu.uea.seldonfleet.domain.model.DeviceStatus
import br.edu.uea.seldonfleet.domain.model.ReasonCode
import java.util.Locale

object StatusFormatter {

    fun labelFor(status: DeviceStatus): String = when (status) {
        DeviceStatus.OK -> "OK"
        DeviceStatus.ATENCAO -> "ATENÇÃO"
        DeviceStatus.CRITICO -> "CRÍTICO"
        DeviceStatus.INDETERMINADO -> "SEM DADOS"
    }

    fun formatJustification(result: ClassificationResult): String {
        val prefix = labelFor(result.status)
        if (result.reasons.isEmpty()) return "$prefix: classificação concluída."

        val details = result.reasons.joinToString("; ") { reason ->
            when (reason.code) {
                ReasonCode.MISSING_BATTERY_CAPACITY ->
                    "capacidade da bateria ausente ou inválida (${reason.observedValue.toInt()} mAh), impedindo o cálculo do dreno relativo"
                ReasonCode.ZERO_SCREEN_TIME ->
                    "tempo de tela igual ou inferior a zero (${reason.observedValue.toInt()} min), impedindo divisão para cálculo de dreno horário"
                ReasonCode.INVALID_ENERGY_CONVERSION ->
                    "unidade de energia ou tensão nominal inválida para normalização em mAh"
                ReasonCode.NEGATIVE_ENERGY ->
                    "consumo energético negativo ou ausente inválido (${fmt(reason.observedValue)} mAh)"
                ReasonCode.INVALID_JANK_METRIC ->
                    "percentual de jank fora do intervalo físico [0%, 100%] (${fmt(reason.observedValue)}%)"
                ReasonCode.INVALID_CRASH_METRIC ->
                    "contagem de falhas negativa ou ausente (${reason.observedValue.toInt()})"
                ReasonCode.HIGH_RELATIVE_DRAIN ->
                    "dreno de ${fmt(reason.observedValue)}%/h, acima do limiar de ${fmt(reason.thresholdValue)}%/h"
                ReasonCode.HIGH_CRASH_COUNT ->
                    "${reason.observedValue.toInt()} falhas em 7 dias, atingindo o limiar de ${reason.thresholdValue.toInt()} falhas"
                ReasonCode.HIGH_JANK_FRAMES ->
                    "taxa de quadros perdidos (jank) de ${fmt(reason.observedValue)}%, acima do limiar de ${fmt(reason.thresholdValue)}%"
                ReasonCode.HEALTHY ->
                    "dreno de ${fmt(reason.observedValue)}%/h (abaixo do limiar de ${fmt(reason.thresholdValue)}%/h), sem falhas críticas ou engasgos gráficos"
            }
        }
        return "$prefix: $details."
    }

    fun fmt(value: Double): String = String.format(Locale.forLanguageTag("pt-BR"), "%.1f", value)
}
