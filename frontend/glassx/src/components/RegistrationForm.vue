<template>
  <div class="w-full max-w-xl flex flex-col items-center">
    <div class="relative w-full max-w-md mb-6">
        <div class="flex items-center justify-center gap-2 text-xs md:text-sm">
          <div class="font-medium transition-colors" :class="currentStep === 'basic' ? 'text-blue-200' : 'text-white/60'">1. {{ $t('register.steps.basic') }}</div>
          <div class="w-5 h-px bg-white/5"></div>
          <template v-if="questionnaireEnabled">
            <div class="font-medium transition-colors" :class="currentStep === 'questionnaire' ? 'text-blue-200' : 'text-white/60'">2. {{ $t('register.steps.questionnaire') }}</div>
            <div class="w-5 h-px bg-white/5"></div>
            <div class="font-medium transition-colors" :class="currentStep === 'submit' ? 'text-blue-200' : 'text-white/60'">3. {{ $t('register.steps.submit') }}</div>
          </template>
          <div v-else class="font-medium transition-colors" :class="currentStep === 'submit' ? 'text-blue-200' : 'text-white/60'">2. {{ $t('register.steps.submit') }}</div>
        </div>
    </div>

    <div class="w-full max-w-xl">
      <div class="lg:hidden text-center mb-6">
        <h2 class="text-2xl font-bold text-white">{{ $t('register.title') }}</h2>
        <p class="text-white/60">{{ $t('register.subtitle') }}</p>
      </div>

      <div class="relative">
         <form v-if="currentStep === 'basic'" @submit.prevent="goToQuestionnaire" class="space-y-5">
          <div class="space-y-3">
            <div v-if="bedrockEnabled">
              <Label class="mb-2">{{ $t('register.form.platform') }}</Label>
              <div class="inline-flex w-full rounded-lg bg-white/5 border border-white/10 p-1 gap-1" role="radiogroup" :aria-label="$t('register.form.platform')">
                <Button
                  type="button"
                  variant="outline"
                  class="flex-1 border-transparent focus:ring-offset-0"
                  :class="selectedPlatform === 'java' ? 'bg-white/20 text-white shadow-sm hover:bg-white/20' : 'text-white/60 hover:bg-white/5 hover:text-white'"
                  @click="selectPlatform('java')"
                >
                  {{ $t('register.form.platform_java') }}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  class="flex-1 border-transparent focus:ring-offset-0"
                  :class="selectedPlatform === 'bedrock' ? 'bg-white/20 text-white shadow-sm hover:bg-white/20' : 'text-white/60 hover:bg-white/5 hover:text-white'"
                  @click="selectPlatform('bedrock')"
                >
                  {{ $t('register.form.platform_bedrock') }}
                </Button>
              </div>
              <p v-if="selectedPlatform === 'bedrock'" class="mt-2 text-xs text-white/60">
                {{ $t('register.form.platform_bedrock_prefix_hint', { prefix: bedrockPrefix }) }}
              </p>
            </div>

            <div>
              <Label for="username" class="mb-1">{{ $t('register.form.username') }}</Label>
              <Input
                id="username"
                v-model="form.username"
                type="text"
                :placeholder="$t('register.form.username_placeholder')"
                :class="{ 'border-red-500 focus-visible:ring-red-500': errors.username }"
                @blur="validateUsername"
              />
              <p v-if="errors.username" class="mt-1 text-sm text-red-400">{{ errors.username }}</p>
            </div>

            <div>
              <Label for="email" class="mb-1">{{ $t('register.form.email') }}</Label>
              <Input
                id="email"
                v-model="form.email"
                type="email"
                :placeholder="$t('register.form.email_placeholder')"
                :class="{ 'border-red-500 focus-visible:ring-red-500': errors.email }"
                @blur="validateEmail"
              />
              <p v-if="errors.email" class="mt-1 text-sm text-red-400">{{ errors.email }}</p>
            </div>

            <div>
              <Label for="password" class="mb-1">{{ $t('register.form.password') }}</Label>
              <Input
                id="password"
                v-model="form.password"
                type="password"
                :placeholder="$t('register.form.password_placeholder')"
                :class="{ 'border-red-500 focus-visible:ring-red-500': errors.password }"
                @blur="validatePassword"
              />
              <p v-if="errors.password" class="mt-1 text-sm text-red-400">{{ errors.password }}</p>
              <p v-if="authmeConfig.passwordRegex" class="mt-1 text-xs text-white/50">{{ $t('register.form.password_hint', { regex: authmeConfig.passwordRegex }) }}</p>
            </div>

            <VerificationProgress
              :must-methods="authMethodsState.mustMethods"
              :optional-methods="authMethodsState.optionalMethods"
              :min-optional-required="authMethodsState.minOptionalRequired"
              :completed-methods="completedMethods"
              :is-method-enabled="isMethodEnabled"
            />

            <div v-if="isMethodEnabled('email') && showEmailVerification">
              <Label for="code" class="mb-1 flex items-center gap-2">
                {{ $t('register.form.code') }}
                <span v-if="isMethodRequired('email')" class="text-xs px-1.5 py-0.5 rounded bg-red-500/20 text-red-300">{{ $t('auth.required') }}</span>
                <span v-else class="text-xs px-1.5 py-0.5 rounded bg-blue-500/20 text-blue-300">{{ $t('auth.optional') }}</span>
              </Label>
              <div class="flex flex-col sm:flex-row gap-2">
                <Input
                  id="code"
                  v-model="form.code"
                  type="text"
                  :placeholder="$t('register.form.code_placeholder')"
                  :class="{ 'border-red-500 focus-visible:ring-red-500': errors.code }"
                  @blur="validateCode"
                  @input="onEmailCodeChange"
                />
                <Button
                  type="button"
                  variant="secondary"
                  @click="sendCode"
                  :disabled="sending || !form.email || emailCooldownSeconds > 0"
                  class="whitespace-nowrap"
                >
                  {{ sending ? $t('register.sending') : emailCooldownSeconds > 0 ? `${emailCooldownSeconds}s` : $t('register.sendCode') }}
                </Button>
              </div>
              <p v-if="errors.code" class="mt-1 text-sm text-red-400">{{ errors.code }}</p>
            </div>

            <div v-if="isMethodEnabled('sms') && showSmsVerification">
              <Label class="mb-1 flex items-center gap-2">
                {{ $t('sms.title') }}
                <span v-if="isMethodRequired('sms')" class="text-xs px-1.5 py-0.5 rounded bg-red-500/20 text-red-300">{{ $t('auth.required') }}</span>
                <span v-else class="text-xs px-1.5 py-0.5 rounded bg-blue-500/20 text-blue-300">{{ $t('auth.optional') }}</span>
              </Label>
              <PhoneInput
                v-model="form.phone"
                v-model:country-code="form.countryCode"
                v-model:code="form.smsCode"
                :country-codes="smsCountryCodes"
                :error="errors.phone"
                :code-error="errors.smsCode"
                @update:code="onSmsCodeChange"
              />
            </div>

            <div v-if="isMethodEnabled('captcha') && showCaptchaVerification">
              <Label for="captcha" class="mb-1 flex items-center gap-2">
                {{ $t('register.form.captcha') }}
                <span v-if="isMethodRequired('captcha')" class="text-xs px-1.5 py-0.5 rounded bg-red-500/20 text-red-300">{{ $t('auth.required') }}</span>
                <span v-else class="text-xs px-1.5 py-0.5 rounded bg-blue-500/20 text-blue-300">{{ $t('auth.optional') }}</span>
              </Label>
              <div class="flex flex-col sm:flex-row gap-2 items-center">
                <Input
                  id="captcha"
                  v-model="form.captchaAnswer"
                  type="text"
                  :placeholder="$t('register.form.captcha_placeholder')"
                  :class="{ 'border-red-500 focus-visible:ring-red-500': errors.captcha }"
                  @blur="validateCaptcha"
                  @input="onCaptchaChange"
                />
                <div class="cursor-pointer border border-white/10 rounded-lg overflow-hidden bg-white/5 backdrop-blur-sm hover:bg-white/15 hover:border-white/25 transition-all duration-300 flex-shrink-0 shadow-lg" @click="refreshCaptcha" :title="$t('register.form.captcha_refresh')">
                  <img v-if="captchaImage" :src="captchaImage" alt="captcha" class="h-10 w-auto" />
                  <div v-else class="h-10 w-28 flex items-center justify-center text-white/60 text-sm">{{ $t('common.loading') }}</div>
                </div>
              </div>
              <p v-if="errors.captcha" class="mt-1 text-sm text-red-400">{{ errors.captcha }}</p>
              <p class="mt-1 text-xs text-white/50">{{ $t('register.form.captcha_hint') }}</p>
            </div>

            <div v-if="isMethodEnabled('discord') && showDiscordVerification" class="pt-2">
              <Label class="mb-2 flex items-center gap-2">
                Discord
                <span v-if="isMethodRequired('discord')" class="text-xs px-1.5 py-0.5 rounded bg-red-500/20 text-red-300">{{ $t('auth.required') }}</span>
                <span v-else class="text-xs px-1.5 py-0.5 rounded bg-blue-500/20 text-blue-300">{{ $t('auth.optional') }}</span>
              </Label>
              <DiscordLink :username="getNormalizedUsername()" :required="isMethodRequired('discord')" @linked="onDiscordLinked" @unlinked="onDiscordUnlinked" />
              <p v-if="errors.discord" class="mt-1 text-sm text-red-400">{{ errors.discord }}</p>
            </div>
          </div>

          <Button type="submit" :disabled="!isBasicStepValid" class="w-full">
            <span>{{ questionnaireEnabled ? $t('register.actions.next_questionnaire') : $t('register.steps.submit') }}</span>
          </Button>
        </form>

        <div v-else-if="currentStep === 'questionnaire'">
          <QuestionnaireForm @back="currentStep = 'basic'" @skip="onQuestionnaireSkipped" @passed="onQuestionnairePassed" />
        </div>

        <div v-else class="space-y-4">
          <div class="rounded-lg border border-white/10 bg-white/5 p-4 text-sm text-white/80">
            <p class="mb-1"><strong>{{ $t('register.summary.username') }}:</strong> {{ getNormalizedUsername() }}</p>
            <p class="mb-1"><strong>{{ $t('register.summary.email') }}:</strong> {{ form.email }}</p>
            <p v-if="questionnaireResult"><strong>{{ $t('register.summary.questionnaire') }}:</strong> {{ questionnaireResult.passed ? $t('questionnaire.passed') : $t('questionnaire.failed') }} ({{ questionnaireResult.score }}/{{ questionnaireResult.passScore }})</p>
          </div>

          <div class="rounded-lg border border-white/10 bg-white/5 p-6 text-center">
            <div v-if="loading" class="flex flex-col items-center gap-3 text-white/70">
              <div class="h-6 w-6 animate-spin rounded-full border-2 border-white border-t-transparent"></div>
              <p>{{ $t('common.loading') }}</p>
            </div>
            <p v-else-if="registrationSubmitted" class="text-green-300 font-medium">{{ registrationSuccessMessage }}</p>
            <p v-else class="text-red-300 font-medium">{{ $t('register.failed') }}</p>
          </div>
        </div>
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
import DiscordLink from '@/components/DiscordLink.vue'
import QuestionnaireForm from '@/components/QuestionnaireForm.vue'
import PhoneInput from '@/components/PhoneInput.vue'
import VerificationProgress from '@/components/VerificationProgress.vue'
import type { ConfigResponse, QuestionnaireSubmission, RegisterRequest } from '@/services/api'

import Button from './ui/Button.vue'
import Input from './ui/Input.vue'
import Label from './ui/Label.vue'

const { t, locale } = useI18n()
const { success, error } = useNotification()

type RegisterStep = 'basic' | 'questionnaire' | 'submit'
const currentStep = ref<RegisterStep>('basic')

const loading = ref(false)
const sending = ref(false)
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

const questionnaireResult = ref<QuestionnaireSubmission | null>(null)
const questionnaireEnabled = computed(() => config.value.questionnaire?.enabled || false)
const questionnaireRequired = computed(() => questionnaireEnabled.value)
const bedrockEnabled = computed(() => config.value.bedrock?.enabled || false)
const bedrockPrefix = computed(() => config.value.bedrock?.prefix || '.')
const selectedPlatform = ref<'java' | 'bedrock'>('java')

const authmeConfig = computed(() => config.value.authme)

const {
  authState: authMethodsState,
  isMethodEnabled,
  isMethodRequired,
  setMethodCompleted,
  canSubmit: authCanSubmit,
  getMissingRequiredMethods,
  getOptionalMethodsProgress,
  resetAll: resetAuthMethods
} = useAuthMethods({ config })

const smsCountryCodes = computed(() => config.value.sms?.countryCodes || ['+86', '+1', '+44', '+81', '+82', '+852', '+853', '+886'])

const showEmailVerification = computed(() => isMethodEnabled('email'))
const showSmsVerification = computed(() => isMethodEnabled('sms'))
const showCaptchaVerification = computed(() => isMethodEnabled('captcha'))
const showDiscordVerification = computed(() => isMethodEnabled('discord'))

const completedMethods = computed<AuthMethodType[]>(() => {
  const completed: AuthMethodType[] = []
  if (form.code && !errors.code) completed.push('email')
  if (form.smsCode && !errors.smsCode) completed.push('sms')
  if (form.captchaAnswer && !errors.captcha) completed.push('captcha')
  if (discordLinked.value) completed.push('discord')
  return completed
})

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

const form = reactive({
  username: '',
  email: '',
  code: '',
  password: '',
  captchaAnswer: '',
  phone: '',
  countryCode: '+86',
  smsCode: ''
})
const errors = reactive({
  username: '',
  email: '',
  code: '',
  password: '',
  captcha: '',
  discord: '',
  phone: '',
  smsCode: ''
})

const onDiscordLinked = () => {
  discordLinked.value = true
  errors.discord = ''
  setMethodCompleted('discord', true)
}
const onDiscordUnlinked = () => {
  discordLinked.value = false
  setMethodCompleted('discord', false)
}

const onEmailCodeChange = () => {
  if (form.code && !errors.code) {
    setMethodCompleted('email', true)
  } else {
    setMethodCompleted('email', false)
  }
}

const onSmsCodeChange = () => {
  if (form.smsCode && !errors.smsCode) {
    setMethodCompleted('sms', true)
  } else {
    setMethodCompleted('sms', false)
  }
}

const onCaptchaChange = () => {
  if (form.captchaAnswer && !errors.captcha) {
    setMethodCompleted('captcha', true)
  } else {
    setMethodCompleted('captcha', false)
  }
}

const validateDiscord = () => {
  errors.discord = ''
  if (isMethodRequired('discord') && !discordLinked.value) {
    errors.discord = t('discord.required')
  }
}
const validateUsername = () => {
  errors.username = ''
  const rawUsername = getUsernameForValidation()
  if (!rawUsername) {
    errors.username = t('register.validation.username_required')
    return
  }

  const regex = config.value.usernameRegex || '^[a-zA-Z0-9_-]{3,16}$'
  if (regex && !new RegExp(regex).test(rawUsername)) {
    errors.username = t('register.validation.username_format', { regex })
  } else if (!regex && !/^[a-zA-Z0-9_]+$/.test(rawUsername)) {
    errors.username = t('register.validation.username_format', { regex: '^[a-zA-Z0-9_]+$' })
  }
}

const selectPlatform = (platform: 'java' | 'bedrock') => {
  selectedPlatform.value = platform
  normalizeUsernameForPlatform()
  validateUsername()
}

const normalizeUsernameForPlatform = () => {
  const trimmedUsername = form.username.trim()
  const rawUsername = bedrockEnabled.value ? stripBedrockPrefixes(trimmedUsername) : trimmedUsername
  if (!rawUsername) {
    form.username = ''
    return
  }

  if (selectedPlatform.value === 'bedrock' && bedrockEnabled.value) {
    form.username = `${bedrockPrefix.value}${rawUsername}`
    return
  }

  form.username = rawUsername
}

const getNormalizedUsername = () => {
  const rawUsername = getUsernameForValidation()
  if (selectedPlatform.value === 'bedrock' && bedrockEnabled.value && rawUsername) {
    return `${bedrockPrefix.value}${rawUsername}`
  }
  return rawUsername
}

const getUsernameForValidation = () => {
  const trimmedUsername = form.username.trim()
  if (!trimmedUsername) {
    return ''
  }

  if (selectedPlatform.value === 'bedrock' && bedrockEnabled.value) {
    return stripBedrockPrefixes(trimmedUsername)
  }

  return trimmedUsername
}

const stripBedrockPrefixes = (username: string) => {
  const prefix = bedrockPrefix.value
  if (!prefix) {
    return username
  }

  let rawUsername = username
  while (rawUsername.startsWith(prefix)) {
    rawUsername = rawUsername.slice(prefix.length)
  }
  return rawUsername
}

watch(() => form.username, () => {
  if (selectedPlatform.value === 'bedrock' && bedrockEnabled.value && form.username.trim()) {
    normalizeUsernameForPlatform()
  }
})
const validateEmail = () => {
  errors.email = ''
  if (!form.email) {
    errors.email = t('register.validation.email_required')
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
    errors.email = t('register.validation.email_format')
  }
}
const validatePassword = () => {
  errors.password = ''
  if (!form.password) {
    errors.password = t('register.validation.password_required')
  } else if (authmeConfig.value?.passwordRegex && !new RegExp(authmeConfig.value.passwordRegex).test(form.password)) {
    errors.password = t('register.validation.password_format', { regex: authmeConfig.value.passwordRegex })
  }
}
const validateCode = () => {
  errors.code = ''
  if (isMethodEnabled('email') && isMethodRequired('email') && !form.code) {
    errors.code = t('register.validation.code_required')
  }
}
const validateCaptcha = () => {
  errors.captcha = ''
  if (isMethodEnabled('captcha') && isMethodRequired('captcha') && !form.captchaAnswer) {
    errors.captcha = t('register.validation.captcha_required')
  }
}
const validatePhone = () => {
  errors.phone = ''
  if (isMethodEnabled('sms') && isMethodRequired('sms')) {
    if (!form.phone) {
      errors.phone = t('sms.invalidPhone')
    }
  }
}
const validateSmsCode = () => {
  errors.smsCode = ''
  if (isMethodEnabled('sms') && isMethodRequired('sms') && !form.smsCode) {
    errors.smsCode = t('register.validation.code_required')
  }
}

const validateForm = () => {
  validateUsername()
  validateEmail()
  validatePassword()
  validateCode()
  validateCaptcha()
  validateDiscord()
  validatePhone()
  validateSmsCode()
}

const isBasicStepValid = computed(() => {
  let valid = form.username && form.email && !errors.username && !errors.email
  valid = valid && !!form.password && !errors.password

  if (isMethodEnabled('email')) {
    if (isMethodRequired('email')) {
      valid = valid && !!form.code && !errors.code
    }
  }

  if (isMethodEnabled('sms')) {
    if (isMethodRequired('sms')) {
      valid = valid && !!form.phone && !errors.phone && !!form.smsCode && !errors.smsCode
    }
  }

  if (isMethodEnabled('captcha')) {
    if (isMethodRequired('captcha')) {
      valid = valid && !!form.captchaAnswer && !errors.captcha
    }
  }

  if (isMethodEnabled('discord')) {
    if (isMethodRequired('discord')) {
      valid = valid && discordLinked.value && !errors.discord
    }
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

const onQuestionnaireSkipped = () => {
  if (questionnaireEnabled.value) {
    error(t('register.questionnaire.required'))
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

const { cooldownSeconds: emailCooldownSeconds, startCooldown: startEmailCooldown, stopCooldown: stopEmailCooldown } = useCooldown()

onUnmounted(() => {
  stopEmailCooldown()
})

const sendCode = async () => {
  if (sending.value || emailCooldownSeconds.value > 0) return
  validateEmail()
  if (errors.email) return
  sending.value = true
  try {
    const email = form.email.trim().toLowerCase()
    const res = await apiService.sendCode({ email, language: locale.value })
    if (res.success) {
      success(t('register.codeSent'))
      startEmailCooldown(60)
    } else if (res.remainingSeconds && res.remainingSeconds > 0) {
      startEmailCooldown(res.remainingSeconds)
      error(res.message || t('register.sendFailed'))
    } else {
      error(res.message || t('register.sendFailed'))
    }
  } catch {
    error(t('register.sendFailed'))
  } finally {
    sending.value = false
  }
}

const handleSubmit = async () => {
  if (loading.value) return
  validateForm()
  if (!isFinalStepValid.value) return
  loading.value = true
  registrationSubmitted.value = false
  registrationSuccessMessage.value = ""
  try {
    const registerData: RegisterRequest & { phone?: string; countryCode?: string; smsCode?: string } = {
      username: getNormalizedUsername(),
      email: form.email.trim().toLowerCase(),
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

    const response = await apiService.register(registerData as RegisterRequest)
    if (response.success) {
      registrationSubmitted.value = true
      registrationSuccessMessage.value = response.message || t('register.success')
      success(registrationSuccessMessage.value)
    } else {
      registrationSubmitted.value = false
      error(response.message || t('register.failed'))
      if (isMethodEnabled('captcha')) await refreshCaptcha()
    }
  } catch {
    registrationSubmitted.value = false
    error(t('register.failed'))
    if (isMethodEnabled('captcha')) await refreshCaptcha()
  } finally {
    loading.value = false
  }
}
</script>
