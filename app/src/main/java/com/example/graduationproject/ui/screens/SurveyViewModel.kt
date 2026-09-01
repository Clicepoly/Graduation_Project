package com.example.graduationproject.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

sealed class SurveyStep(
    val title: String,
    val description: String,
    val type: StepType
) {
    enum class StepType { TIMER, YES_NO }

    object Sppb1A : SurveyStep("SPPB 1A: 雙腳並排站立", "請雙腳併攏站立，盡可能維持平衡直到 10 秒。", StepType.TIMER)
    object Sppb1B : SurveyStep("SPPB 1B: 雙腳半並排站立", "請將一腳足弓貼住另一腳大姆指旁站立直到 10 秒。", StepType.TIMER)
    object Sppb1C : SurveyStep("SPPB 1C: 雙腳直線站立", "請將一腳跟貼住另一腳尖，呈直線站立直到 10 秒。", StepType.TIMER)
    object Sppb2 : SurveyStep("SPPB 2: 4 公尺步行速度", "請依正常步速行走 4 公尺。", StepType.TIMER)
    object Sppb3 : SurveyStep("SPPB 3: 5 次從椅子起身", "雙手交叉胸前，盡快完成 5 次坐下與起身。", StepType.TIMER)
    object FallRisk1 : SurveyStep("FallRisk 1: 跌倒史", "過去一年是否曾跌倒 >= 2 次，或曾因跌倒就醫？", StepType.YES_NO)
    object FallRisk2 : SurveyStep("FallRisk 2: TUG 測試", "3 公尺起身行走 (TUG) 是否超過 20 秒？", StepType.YES_NO)
    object FallRisk3 : SurveyStep("FallRisk 3: 步行速度", "6 公尺步行速度是否超過 7.5 秒？", StepType.YES_NO)
    object FallRisk4 : SurveyStep("FallRisk 4: 認知狀態", "長輩目前是否具備中度認知退化跡象？", StepType.YES_NO)
}

data class SurveyUiState(
    val currentStepIndex: Int = 0,
    val isCompleted: Boolean = false,
    val timerValue: Float = 0f,
    val isTimerRunning: Boolean = false,
    val finalGrade: String = "",
    val finalScore: Int = 0,
    val hasFallRisk: Boolean = false,
    val lastMeasurementText: String = "",
    val lastMeasurementScore: Int? = null,
    val lastMeasurementValue: Float = 0f,
    val isStartEnabled: Boolean = true
)

class SurveyViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SurveyUiState())
    val uiState = _uiState.asStateFlow()

    val steps = listOf(
        SurveyStep.Sppb1A, SurveyStep.Sppb1B, SurveyStep.Sppb1C,
        SurveyStep.Sppb2, SurveyStep.Sppb3,
        SurveyStep.FallRisk1, SurveyStep.FallRisk2, SurveyStep.FallRisk3, SurveyStep.FallRisk4
    )

    private val inputs = mutableMapOf<Int, Any>()
    private var timerJob: Job? = null

    val currentStep: SurveyStep
        get() = steps[_uiState.value.currentStepIndex]

    val progress: Float
        get() = (_uiState.value.currentStepIndex + 1).toFloat() / steps.size

    fun startTimer() {
        if (_uiState.value.isTimerRunning) return
        //_uiState.update { it.copy(isTimerRunning = true) }
        timerJob = viewModelScope.launch {

//            while (true) {
//                delay(100)
//                _uiState.update { it.copy(timerValue = it.timerValue + 0.1f) }
//            }
        }
    }

    fun pauseTimer() {
        timerJob?.cancel()
        _uiState.update { it.copy(isTimerRunning = false) }
    }

    fun resetTimer() {
        pauseTimer()
        _uiState.update { it.copy(timerValue = 0f) }
    }

    fun startCameraTest() {
        _uiState.update { it.copy(isStartEnabled = false, isTimerRunning = false) }
    }

    fun retakeCurrentCameraTest() {
        resetTimer()
        _uiState.update {
            it.copy(
                isStartEnabled = true,
                lastMeasurementText = "",
                lastMeasurementScore = null
            )
        }
    }

    fun prepareNextStep() {
        _uiState.update {
            it.copy(
                isStartEnabled = true,
                lastMeasurementText = "",
                lastMeasurementScore = null,
                lastMeasurementValue = 0f,
                timerValue = 0f,
                isTimerRunning = false
            )
        }
    }

    fun applyTimerToCurrentStep() {
        submitValue(_uiState.value.timerValue)
    }

    private fun thresholdForCurrentStep(): Float = when (currentStep) {
        SurveyStep.FallRisk2 -> 20f
        SurveyStep.FallRisk3 -> 7.5f
        else -> 0f
    }

    fun submitCurrentCameraDecision() {
        val currentIndex = _uiState.value.currentStepIndex
        val measuredValue = _uiState.value.lastMeasurementValue
        val exceedsThreshold = measuredValue > thresholdForCurrentStep()
        inputs[currentIndex] = exceedsThreshold

        val nextIndex = currentIndex + 1
        if (nextIndex >= steps.size) {
            calculateResult()
        } else {
            _uiState.update { it.copy(currentStepIndex = nextIndex) }
            prepareNextStep()
        }
    }

    fun scoreForWalkTime(tWalk: Float): Int = when {
        tWalk > 0f && tWalk < 4.82f -> 4
        tWalk <= 6.20f -> 3
        tWalk <= 8.70f -> 2
        tWalk > 8.70f -> 1
        else -> 0
    }

    fun scoreForStandTime(tStand: Float): Int = when {
        tStand > 0f && tStand < 11.19f -> 4
        tStand <= 13.69f -> 3
        tStand <= 16.69f -> 2
        tStand <= 59f -> 1
        else -> 0
    }

    fun scoreForBalanceStages(t1a: Float, t1b: Float, t1c: Float): Int {
        var score = 0
        if (t1a >= 10f) score += 1
        if (t1b >= 10f) score += 1
        score += when {
            t1c >= 10f -> 2
            t1c >= 3f -> 1
            else -> 0
        }
        return score
    }

    fun submitCameraMeasurement(value: Float, label: String = "本次測試", stageValues: List<Float> = emptyList()) {
        val currentIndex = _uiState.value.currentStepIndex
        val stageTimes = stageValues.ifEmpty { listOf(value) }

        _uiState.update { it.copy(isStartEnabled = false, lastMeasurementValue = value) }

        if (currentStep == SurveyStep.Sppb1A && stageTimes.size >= 3) {
            inputs[0] = stageTimes[0]
            inputs[1] = stageTimes[1]
            inputs[2] = stageTimes[2]
        } else if (currentStep == SurveyStep.FallRisk2 || currentStep == SurveyStep.FallRisk3) {
            val exceedsThreshold = value > thresholdForCurrentStep()
            inputs[currentIndex] = exceedsThreshold
        } else {
            inputs[currentIndex] = value
        }
        resetTimer()

        val stepScore = when {
            currentStep == SurveyStep.Sppb1A && stageTimes.size >= 3 -> scoreForBalanceStages(stageTimes[0], stageTimes[1], stageTimes[2])
            currentStep == SurveyStep.Sppb1B -> scoreForBalanceStages(inputs[0] as? Float ?: 0f, value, inputs[2] as? Float ?: 0f)
            currentStep == SurveyStep.Sppb1C -> scoreForBalanceStages(inputs[0] as? Float ?: 0f, inputs[1] as? Float ?: 0f, value)
            currentStep == SurveyStep.Sppb2 -> scoreForWalkTime(value)
            currentStep == SurveyStep.Sppb3 -> scoreForStandTime(value)
            currentStep == SurveyStep.FallRisk2 -> if (value > 20f) 1 else 0
            currentStep == SurveyStep.FallRisk3 -> if (value > 7.5f) 1 else 0
            else -> 0
        }

        val debugText = when {
            currentStep == SurveyStep.Sppb1A && stageTimes.size >= 3 -> {
                val t1a = stageTimes[0]
                val t1b = stageTimes[1]
                val t1c = stageTimes[2]
                "${label}：1A=${String.format(Locale.US, "%.1f", t1a)}秒，1B=${String.format(Locale.US, "%.1f", t1b)}秒，1C=${String.format(Locale.US, "%.1f", t1c)}秒 → ${stepScore} 分"
            }
            currentStep == SurveyStep.FallRisk2 -> {
                val aboveThreshold = value > 20f
                "${label}：${String.format(Locale.US, "%.1f", value)}秒 → ${if (aboveThreshold) "超過 20 秒" else "未超過 20 秒"}"
            }
            currentStep == SurveyStep.FallRisk3 -> {
                val aboveThreshold = value > 7.5f
                "${label}：${String.format(Locale.US, "%.1f", value)}秒 → ${if (aboveThreshold) "超過 7.5 秒" else "未超過 7.5 秒"}"
            }
            else -> {
                buildString {
                    append(label)
                    append("：")
                    append(String.format(Locale.US, "%.1f 秒", value))
                    append(" → ")
                    append(stepScore)
                    append(" 分")
                }
            }
        }

        _uiState.update {
            it.copy(
                lastMeasurementText = debugText,
                lastMeasurementScore = if (currentStep == SurveyStep.FallRisk2 || currentStep == SurveyStep.FallRisk3) null else stepScore
            )
        }
    }

    fun retakeCurrentMeasurement() {
        _uiState.update {
            it.copy(
                lastMeasurementText = "",
                lastMeasurementScore = null,
                lastMeasurementValue = 0f,
                isStartEnabled = true
            )
        }
    }

    fun submitValue(value: Any) {
        val currentIndex = _uiState.value.currentStepIndex
        inputs[currentIndex] = value
        resetTimer()

        // VIVIFRAIL 跳題邏輯: 1A 或 1B 未達 10 秒，直接跳到步速測試 (Index 3)
        val nextIndex = when {
            (currentStep == SurveyStep.Sppb1A && (value as? Float ?: 0f) < 10f) -> 3
            (currentStep == SurveyStep.Sppb1B && (value as? Float ?: 0f) < 10f) -> 3
            else -> currentIndex + 1
        }

        if (nextIndex >= steps.size) {
            calculateResult()
        } else {
            _uiState.update { it.copy(currentStepIndex = nextIndex) }
            prepareNextStep()
        }
    }

    private fun calculateResult() {
        var sppbScore = 0

        // 站立 (1ABC)
        val t1a = inputs[0] as? Float ?: 0f
        val t1b = inputs[1] as? Float ?: 0f
        val t1c = inputs[2] as? Float ?: 0f

        sppbScore += scoreForBalanceStages(t1a, t1b, t1c)

        // 4公尺
        val tWalk = inputs[3] as? Float ?: 0f
        sppbScore += scoreForWalkTime(tWalk)

        // 5次起身
        val tStand = inputs[4] as? Float ?: 0f
        sppbScore += scoreForStandTime(tStand)

        // fallrisk
        val hasFallRisk = (5..8).any { inputs[it] == true }

        val grade = when (sppbScore) {
            in 0..3 -> "A"
            in 4..6 -> if (hasFallRisk) "B+" else "B"
            in 7..9 -> if (hasFallRisk) "C+" else "C"
            else -> "D"
        }

        _uiState.update {
            it.copy(
                isCompleted = true,
                finalScore = sppbScore,
                finalGrade = grade,
                hasFallRisk = hasFallRisk
            )
        }
    }
}
