<template>
  <div class="w-full max-w-xl flex flex-col items-center">
    <div class="relative w-full max-w-md mb-6">
      <div class="flex items-center justify-center gap-2 text-xs md:text-sm">
        <div class="font-medium transition-colors" :class="currentStep === 'basic' ? 'text-blue-200' : 'text-white/60'">
          1. {{ $t('register.steps.basic') }}
        </div>
        <div class="w-5 h-px bg-white/5"></div>
        <template v-if="questionnaireEnabled">
          <div class="font-medium transition-colors" :class="currentStep === 'questionnaire' ? 'text-blue-200' : 'text-white/60'">
            2. {{ $t('register.steps.questionnaire') }}
          </div>
          <div class="w-5 h-px bg-white/5"></div>
          <div class="font-medium transition-colors" :class="currentStep === 'submit' ? 'text-blue-200' : 'text-white/60'">
            3. {{ $t('register.steps.submit') }}
          </div>
        </template>
        <div v-else class="font-medium transition-colors" :class="currentStep === 'submit' ? 'text-blue-200' : 'text-white/60'">
          2. {{ $t('register.steps.submit') }}
        </div>
      </div>
    </div>

    <div class="w-full max-w-xl">
      <div class="lg:hidden text-center mb-6">
        <h2 class="text-2xl font-bold text-white">{{ $t('register.title') }}</h2>
        <p class="text-white/60">{{ $t('register.subtitle') }}</p>
      </div>

      <div class="relative">
        <form v-if="currentStep === 'basic'" @submit.prevent="handleBasicSubmit" class="space-y-5">
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

          <AuthMethodsSection
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

          <Button type="submit" :disabled="!isBasicStepValid" class="w-full">
            <span>{{ questionnaireEnabled ? $t('register.actions.next_questionnaire') : $t('register.steps.submit') }}</span>
          </Button>
        </form>

        <div v-else-if="currentStep === 'questionnaire'">
          <QuestionnaireForm
            @back="goToBasic"
            @skip="onQuestionnaireSkipped"
            @passed="onQuestionnairePassed"
          />
        </div>

        <RegistrationSummary
          v-else
          :username="getNormalizedUsername()"
          :email="form.email"
          :loading="loading"
          :registration-submitted="registrationSubmitted"
          :success-message="registrationSuccessMessage"
          :questionnaire-result="questionnaireResult"
        />
      </div>
    </div>
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
import AuthMethodsSection from '@/components/registration/AuthMethodsSection.vue'
import RegistrationSummary from '@/components/registration/RegistrationSummary.vue'
import QuestionnaireForm from '@/components/QuestionnaireForm.vue'
import Button from '@/components/ui/Button.vue'
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
const authMethodsRef = ref<InstanceType<typeof AuthMethodsSection> | null>(null)

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
  let valid = !!form.username && !!form.email && !errors.username && !errors.email
  valid = valid && !!form.password && !errors.password

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
