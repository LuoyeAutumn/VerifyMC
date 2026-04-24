<template>
  <div class="w-full max-w-xl flex flex-col items-center">
    <AnimatedStepper
      ref="stepperRef"
      :initial-step="currentStepIndex"
      :total-steps="totalStepsCount"
      :disable-step-indicators="false"
      class="stepper-container"
      @step-change="handleStepChange"
      @final-step-completed="handleSubmit"
    >
      <template #default="{ currentStep: stepperCurrentStep }">
        <Step v-if="stepperCurrentStep === 1" :title="$t('register.steps.basic')">
          <div class="w-full max-w-xl">
            <div class="lg:hidden text-center mb-6">
              <h2 class="text-2xl font-bold text-white">{{ $t('register.title') }}</h2>
              <p class="text-white/60">{{ $t('register.subtitle') }}</p>
            </div>

            <form @submit.prevent="handleBasicSubmit" class="space-y-5">
              <BasicInfoForm
                ref="basicInfoFormRef"
                v-model:username="form.username"
                v-model:email="form.email"
                v-model:password="form.password"
                v-model:platform="selectedPlatform"
                :bedrock-enabled="bedrockEnabled"
                :bedrock-prefix="bedrockPrefix"
                :username-regex="config.usernameRegex"
                :password-regex="authmeConfig.passwordRegex"
                @submit="handleBasicInfoSubmit"
              >
                <template #submit-text>
                  {{ questionnaireEnabled ? $t('register.actions.next_questionnaire') : $t('register.steps.submit') }}
                </template>
              </BasicInfoForm>

              <VerificationSection
                ref="authMethodsRef"
                :form="form"
                :errors="errors"
                :auth-methods-state="authMethodsState"
                :is-method-enabled="isMethodEnabled"
                :is-method-required="isMethodRequired"
                :set-method-completed="setMethodCompleted"
                :completed-methods="completedMethods"
                :captcha-image="captchaImage"
                :captcha-token="captchaToken"
                :discord-linked="discordLinked"
                :sms-country-codes="smsCountryCodes"
                :normalized-username="getNormalizedUsername()"
                :validate-code="validateCode"
                :validate-captcha="validateCaptcha"
                :validate-email="validateEmail"
                @update:email="handleEmailUpdate"
                @update:code="handleCodeUpdate"
                @update:phone="handlePhoneUpdate"
                @update:country-code="handleCountryCodeUpdate"
                @update:sms-code="handleSmsCodeUpdate"
                @update:captcha-answer="handleCaptchaAnswerUpdate"
                @send-code="handleSendCode"
                @refresh-captcha="refreshCaptcha"
                @discord-linked="handleDiscordLinked"
                @discord-unlinked="handleDiscordUnlinked"
              />
            </form>
          </div>
        </Step>

        <Step v-if="questionnaireEnabled && stepperCurrentStep === 2" :title="$t('register.steps.questionnaire')">
          <div class="w-full max-w-xl">
            <QuestionnaireForm
              @back="goToBasic"
              @skip="onQuestionnaireSkipped"
              @passed="onQuestionnairePassed"
            />
          </div>
        </Step>

        <Step v-if="stepperCurrentStep === totalStepsCount" :title="$t('register.steps.submit')">
          <div class="w-full max-w-xl">
            <RegistrationSummary
              :username="getNormalizedUsername()"
              :email="form.email"
              :loading="loading"
              :registration-submitted="registrationSubmitted"
              :success-message="registrationSuccessMessage"
              :questionnaire-result="questionnaireResult"
            />
          </div>
        </Step>
      </template>
    </AnimatedStepper>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted, watch, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { apiService } from '@/services/api'
import { useNotification } from '@/composables/useNotification'
import { useCooldown } from '@/composables/useCooldown'
import { useAuthMethods, type AuthMethodType } from '@/composables/useAuthMethods'
import { useRegistrationValidation, createValidationForm, createValidationErrors } from '@/composables/useRegistrationValidation'
import { useRegistrationSteps } from '@/composables/useRegistrationSteps'
import BasicInfoForm from '@/components/registration/BasicInfoForm.vue'
import VerificationSection from '@/components/registration/VerificationSection.vue'
import RegistrationSummary from '@/components/registration/RegistrationSummary.vue'
import QuestionnaireForm from '@/components/QuestionnaireForm.vue'
import Button from '@/components/ui/Button.vue'
import AnimatedStepper from '@/components/ui/AnimatedStepper.vue'
import Step from '@/components/ui/Step.vue'
import type { ConfigResponse, QuestionnaireSubmission, RegisterRequest } from '@/services/api'

const { locale } = useI18n()
const { success, error } = useNotification()

const loading = ref(false)
const registrationSubmitted = ref(false)
const registrationSuccessMessage = ref('')
const config = ref<ConfigResponse>({
  authMethods: [],
  theme: '',
  logoUrl: '',
  announcement: '',
  webServerPrefix: '',
  usernameRegex: '',
  authme: { enabled: false, passwordRegex: '^[a-zA-Z0-9_]{8,26}$' },
  captcha: { enabled: false, emailEnabled: true, type: 'math' },
  questionnaire: { enabled: false, passScore: 60 }
})

const captchaImage = ref('')
const captchaToken = ref('')
const discordLinked = ref(false)
const selectedPlatform = ref<'java' | 'bedrock'>('java')

const basicInfoFormRef = ref<InstanceType<typeof BasicInfoForm> | null>(null)
const authMethodsRef = ref<InstanceType<typeof VerificationSection> | null>(null)

const bedrockEnabled = computed(() => config.value.bedrock?.enabled || false)
const bedrockPrefix = computed(() => config.value.bedrock?.prefix || '.')
const authmeConfig = computed(() => config.value.authme)
const smsCountryCodes = computed(() => config.value.sms?.countryCodes || ['+86', '+1', '+44', '+81', '+82', '+852', '+853', '+886'])

const {
  authState: authMethodsState,
  isMethodEnabled,
  isMethodRequired,
  setMethodCompleted,
  canSubmit: authCanSubmit,
  getMissingRequiredMethods,
  getOptionalMethodsProgress
} = useAuthMethods({ config })

const form = reactive(createValidationForm())
const errors = reactive(createValidationErrors())

const validationConfig = computed(() => ({
  usernameRegex: config.value.usernameRegex,
  passwordRegex: authmeConfig.value?.passwordRegex,
  bedrockEnabled: bedrockEnabled.value,
  bedrockPrefix: bedrockPrefix.value
}))

const methodCheckers = {
  isMethodEnabled,
  isMethodRequired
}

const platformState = {
  selectedPlatform,
  discordLinked
}

const {
  validateUsername,
  validateEmail,
  validatePassword,
  validateCode,
  validateCaptcha,
  validateDiscord,
  validatePhone,
  validateSmsCode,
  validateForm,
  getNormalizedUsername,
  clearErrors
} = useRegistrationValidation({
  config: validationConfig,
  form,
  errors,
  methodCheckers,
  platformState
})

const completedMethods = computed<AuthMethodType[]>(() => {
  const completed: AuthMethodType[] = []
  if (form.code && !errors.code) completed.push('email')
  if (form.smsCode && !errors.smsCode) completed.push('sms')
  if (form.captchaAnswer && !errors.captcha) completed.push('captcha')
  if (discordLinked.value) completed.push('discord')
  return completed
})

const isBasicStepValid = computed<boolean>(() => {
  let valid = !!form.username && !errors.username
  valid = valid && !!form.password && !errors.password

  if (isMethodEnabled('email')) {
    valid = valid && !!form.email && !errors.email
  }

  if (isMethodEnabled('email') && isMethodRequired('email')) {
    valid = valid && !!form.code && !errors.code
  }

  if (isMethodEnabled('sms') && isMethodRequired('sms')) {
    valid = valid && !!form.phone && !errors.phone && !!form.smsCode && !errors.smsCode
  }

  if (isMethodEnabled('captcha') && isMethodRequired('captcha')) {
    valid = valid && !!form.captchaAnswer && !errors.captcha
  }

  if (isMethodEnabled('discord') && isMethodRequired('discord')) {
    valid = valid && discordLinked.value && !errors.discord
  }

  const missingRequired = getMissingRequiredMethods()
  if (missingRequired.length > 0) {
    return false
  }

  const optionalProgress = getOptionalMethodsProgress()
  if (optionalProgress.required > 0 && optionalProgress.completed < optionalProgress.required) {
    return false
  }

  return valid
})

const handleSubmit = async () => {
  if (loading.value) return
  loading.value = true
  registrationSubmitted.value = false
  registrationSuccessMessage.value = ''

  try {
    const registerData: RegisterRequest = {
      username: getNormalizedUsername(),
      email: form.email.trim(),
      password: form.password,
      language: locale.value,
      platform: selectedPlatform.value
    }

    if (isMethodEnabled('email') && form.code) registerData.code = form.code
    if (isMethodEnabled('captcha') && form.captchaAnswer) {
      registerData.captchaToken = captchaToken.value
      registerData.captchaAnswer = form.captchaAnswer
    }
    if (isMethodEnabled('sms') && form.smsCode) {
      registerData.phone = form.phone
      registerData.countryCode = form.countryCode
      registerData.smsCode = form.smsCode
    }
    if (questionnaireResult.value) registerData.questionnaire = questionnaireResult.value

    const response = await apiService.register(registerData)
    if (response.success) {
      registrationSubmitted.value = true
      registrationSuccessMessage.value = response.message || ''
      success(registrationSuccessMessage.value)
    } else {
      registrationSubmitted.value = false
      error(response.message || '')
      if (isMethodEnabled('captcha')) await refreshCaptcha()
    }
  } catch {
    registrationSubmitted.value = false
    error('')
    if (isMethodEnabled('captcha')) await refreshCaptcha()
  } finally {
    loading.value = false
  }
}

const {
  currentStep,
  questionnaireResult,
  questionnaireEnabled,
  goToQuestionnaire,
  goToBasic,
  onQuestionnaireSkipped,
  onQuestionnairePassed
} = useRegistrationSteps({
  config,
  isBasicStepValid,
  validateForm,
  handleSubmit,
  onError: (message: string) => error(message)
})

const stepperRef = ref<InstanceType<typeof AnimatedStepper> | null>(null)

const totalStepsCount = computed(() => questionnaireEnabled.value ? 3 : 2)

const currentStepIndex = computed(() => {
  if (currentStep.value === 'basic') return 1
  if (questionnaireEnabled.value) {
    if (currentStep.value === 'questionnaire') return 2
    if (currentStep.value === 'submit') return 3
  } else {
    if (currentStep.value === 'submit') return 2
  }
  return 1
})

const stepIndexToStepName = (index: number): 'basic' | 'questionnaire' | 'submit' => {
  if (index === 1) return 'basic'
  if (questionnaireEnabled.value) {
    if (index === 2) return 'questionnaire'
    if (index === 3) return 'submit'
  } else {
    if (index === 2) return 'submit'
  }
  return 'basic'
}

watch(currentStepIndex, (newIndex) => {
  if (stepperRef.value && stepperRef.value.currentStep !== newIndex) {
    stepperRef.value.updateStep(newIndex)
  }
})

const handleStepChange = (step: number) => {
  const stepName = stepIndexToStepName(step)
  if (stepName === currentStep.value) return
  
  if (stepName === 'basic') {
    goToBasic()
  } else if (stepName === 'questionnaire') {
    if (isBasicStepValid.value) {
      goToQuestionnaire()
    } else {
      if (stepperRef.value) {
        stepperRef.value.updateStep(currentStepIndex.value)
      }
    }
  }
}

const { cooldownSeconds: emailCooldownSeconds, startCooldown: startEmailCooldown, stopCooldown: stopEmailCooldown } = useCooldown()

onMounted(async () => {
  try {
    const res = await apiService.getConfig()
    config.value = res
    if (config.value.captcha?.enabled) {
      await refreshCaptcha()
    }
  } catch (e) {
    console.error('Failed to load config:', e)
  }
})

onUnmounted(() => {
  stopEmailCooldown()
})

const refreshCaptcha = async () => {
  try {
    const response = await apiService.getCaptcha()
    if (response.success && response.token && response.image) {
      captchaToken.value = response.token
      captchaImage.value = response.image
    }
  } catch (e) {
    console.error('Failed to load captcha:', e)
  }
}

const handleBasicSubmit = () => {
  goToQuestionnaire()
}

const handleBasicInfoSubmit = () => {
  goToQuestionnaire()
}

const handleCodeUpdate = (value: string) => {
  form.code = value
  if (value && !errors.code) {
    setMethodCompleted('email', true)
  } else {
    setMethodCompleted('email', false)
  }
}

const handleEmailUpdate = (value: string) => {
  form.email = value
}

const handlePhoneUpdate = (value: string) => {
  form.phone = value
}

const handleCountryCodeUpdate = (value: string) => {
  form.countryCode = value
}

const handleSmsCodeUpdate = (value: string) => {
  form.smsCode = value
  if (value && !errors.smsCode) {
    setMethodCompleted('sms', true)
  } else {
    setMethodCompleted('sms', false)
  }
}

const handleCaptchaAnswerUpdate = (value: string) => {
  form.captchaAnswer = value
  if (value && !errors.captcha) {
    setMethodCompleted('captcha', true)
  } else {
    setMethodCompleted('captcha', false)
  }
}

const handleSendCode = async () => {
  if (emailCooldownSeconds.value > 0) return
  validateEmail()
  if (errors.email) return

  authMethodsRef.value?.setSending(true)
  try {
    const email = form.email.trim()
    const res = await apiService.sendCode({ email, language: locale.value })
    if (res.success) {
      success('')
      startEmailCooldown(60)
      authMethodsRef.value?.startEmailCooldown(60)
    } else if (res.remainingSeconds && res.remainingSeconds > 0) {
      startEmailCooldown(res.remainingSeconds)
      authMethodsRef.value?.startEmailCooldown(res.remainingSeconds)
      error(res.message || '')
    } else {
      error(res.message || '')
    }
  } catch {
    error('')
  } finally {
    authMethodsRef.value?.setSending(false)
  }
}

const handleDiscordLinked = () => {
  discordLinked.value = true
  errors.discord = ''
  setMethodCompleted('discord', true)
}

const handleDiscordUnlinked = () => {
  discordLinked.value = false
  setMethodCompleted('discord', false)
}

watch(() => form.username, () => {
  if (selectedPlatform.value === 'bedrock' && bedrockEnabled.value && form.username.trim()) {
    const trimmedUsername = form.username.trim()
    let rawUsername = trimmedUsername
    while (rawUsername.startsWith(bedrockPrefix.value)) {
      rawUsername = rawUsername.slice(bedrockPrefix.value.length)
    }
    if (rawUsername && selectedPlatform.value === 'bedrock' && bedrockEnabled.value) {
      form.username = `${bedrockPrefix.value}${rawUsername}`
    }
  }
})
</script>

<style scoped>
.stepper-container :deep(> div:last-child) {
  display: none;
}
</style>
