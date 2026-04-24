import { ref, computed, type Ref, type ComputedRef } from 'vue'
import type { ConfigResponse, QuestionnaireSubmission } from '@/services/api'

export type RegisterStep = 'basic' | 'questionnaire' | 'submit'

export interface UseRegistrationStepsOptions {
  config: Ref<ConfigResponse>
  isBasicStepValid: ComputedRef<boolean>
  validateForm: () => void
  handleSubmit: () => Promise<void>
  onError: (message: string) => void
}

export interface UseRegistrationStepsReturn {
  currentStep: Ref<RegisterStep>
  questionnaireResult: Ref<QuestionnaireSubmission | null>
  questionnaireEnabled: ComputedRef<boolean>
  questionnaireRequired: ComputedRef<boolean>
  goToQuestionnaire: () => void
  goToBasic: () => void
  goToSubmit: () => void
  onQuestionnaireSkipped: () => void
  onQuestionnairePassed: (result: QuestionnaireSubmission) => Promise<void>
  isFinalStepValid: ComputedRef<boolean>
  resetSteps: () => void
}

export function useRegistrationSteps(options: UseRegistrationStepsOptions): UseRegistrationStepsReturn {
  const { config, isBasicStepValid, validateForm, handleSubmit, onError } = options

  const currentStep = ref<RegisterStep>('basic')
  const questionnaireResult = ref<QuestionnaireSubmission | null>(null)

  const questionnaireEnabled = computed(() => config.value.questionnaire?.enabled || false)
  const questionnaireRequired = computed(() => questionnaireEnabled.value)

  const isFinalStepValid = computed(() => {
    if (!isBasicStepValid.value) return false
    if (!questionnaireEnabled.value) return true
    if (!questionnaireResult.value) return false
    return questionnaireResult.value.passed === true || questionnaireResult.value.manualReviewRequired === true
  })

  const goToQuestionnaire = () => {
    validateForm()
    if (!isBasicStepValid.value) return
    if (questionnaireRequired.value) {
      currentStep.value = 'questionnaire'
      return
    }
    currentStep.value = 'submit'
    void handleSubmit()
  }

  const goToBasic = () => {
    currentStep.value = 'basic'
  }

  const goToSubmit = () => {
    currentStep.value = 'submit'
  }

  const onQuestionnaireSkipped = () => {
    if (questionnaireEnabled.value) {
      onError('register.questionnaire.required')
      return
    }
    questionnaireResult.value = null
    currentStep.value = 'submit'
    void handleSubmit()
  }

  const onQuestionnairePassed = async (result: QuestionnaireSubmission) => {
    questionnaireResult.value = result
    currentStep.value = 'submit'
    await handleSubmit()
  }

  const resetSteps = () => {
    currentStep.value = 'basic'
    questionnaireResult.value = null
  }

  return {
    currentStep,
    questionnaireResult,
    questionnaireEnabled,
    questionnaireRequired,
    goToQuestionnaire,
    goToBasic,
    goToSubmit,
    onQuestionnaireSkipped,
    onQuestionnairePassed,
    isFinalStepValid,
    resetSteps
  }
}
