import { reactive, computed, type Ref, type ComputedRef } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AuthMethodType } from '@/composables/useAuthMethods'

export interface ValidationConfig {
  usernameRegex?: string
  passwordRegex?: string
  bedrockEnabled?: boolean
  bedrockPrefix?: string
}

export interface ValidationForm {
  username: string
  email: string
  code: string
  password: string
  captchaAnswer: string
  phone: string
  countryCode: string
  smsCode: string
}

export interface ValidationErrors {
  username: string
  email: string
  code: string
  password: string
  captcha: string
  discord: string
  phone: string
  smsCode: string
}

export interface MethodCheckers {
  isMethodEnabled: (method: AuthMethodType) => boolean
  isMethodRequired: (method: AuthMethodType) => boolean
}

export interface PlatformState {
  selectedPlatform: Ref<'java' | 'bedrock'>
  discordLinked: Ref<boolean>
}

export interface UseRegistrationValidationOptions {
  config: Ref<ValidationConfig>
  form?: ValidationForm
  errors?: ValidationErrors
  methodCheckers: MethodCheckers
  platformState: PlatformState
}

export interface UseRegistrationValidationReturn {
  form: ValidationForm
  errors: ValidationErrors
  validateUsername: () => void
  validateEmail: () => void
  validatePassword: () => void
  validateCode: () => void
  validateCaptcha: () => void
  validateDiscord: () => void
  validatePhone: () => void
  validateSmsCode: () => void
  validateForm: () => void
  getNormalizedUsername: () => string
  getUsernameForValidation: () => string
  stripBedrockPrefixes: (username: string) => string
  normalizeUsernameForPlatform: () => void
  selectPlatform: (platform: 'java' | 'bedrock') => void
  clearErrors: () => void
  hasErrors: ComputedRef<boolean>
}

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const DEFAULT_USERNAME_REGEX = '^[a-zA-Z0-9_]+$'
const DEFAULT_USERNAME_REGEX_WITH_DASH = '^[a-zA-Z0-9_-]{3,16}$'

export function useRegistrationValidation(options: UseRegistrationValidationOptions): UseRegistrationValidationReturn {
  const { t } = useI18n()
  const { config, methodCheckers, platformState } = options

  const form = options.form || reactive<ValidationForm>({
    username: '',
    email: '',
    code: '',
    password: '',
    captchaAnswer: '',
    phone: '',
    countryCode: '+86',
    smsCode: ''
  })

  const errors = options.errors || reactive<ValidationErrors>({
    username: '',
    email: '',
    code: '',
    password: '',
    captcha: '',
    discord: '',
    phone: '',
    smsCode: ''
  })

  const { isMethodEnabled, isMethodRequired } = methodCheckers
  const { selectedPlatform, discordLinked } = platformState

  const bedrockEnabled = computed(() => config.value.bedrockEnabled || false)
  const bedrockPrefix = computed(() => config.value.bedrockPrefix || '.')
  const passwordRegex = computed(() => config.value.passwordRegex)

  const stripBedrockPrefixes = (username: string): string => {
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

  const getUsernameForValidation = (): string => {
    const trimmedUsername = form.username.trim()
    if (!trimmedUsername) {
      return ''
    }

    if (selectedPlatform.value === 'bedrock' && bedrockEnabled.value) {
      return stripBedrockPrefixes(trimmedUsername)
    }

    return trimmedUsername
  }

  const getNormalizedUsername = (): string => {
    const rawUsername = getUsernameForValidation()
    if (selectedPlatform.value === 'bedrock' && bedrockEnabled.value && rawUsername) {
      return `${bedrockPrefix.value}${rawUsername}`
    }
    return rawUsername
  }

  const normalizeUsernameForPlatform = (): void => {
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

  const selectPlatform = (platform: 'java' | 'bedrock'): void => {
    selectedPlatform.value = platform
    normalizeUsernameForPlatform()
    validateUsername()
  }

  const validateUsername = (): void => {
    errors.username = ''
    const rawUsername = getUsernameForValidation()
    if (!rawUsername) {
      errors.username = t('register.validation.username_required')
      return
    }

    const regex = config.value.usernameRegex || DEFAULT_USERNAME_REGEX_WITH_DASH
    if (regex && !new RegExp(regex).test(rawUsername)) {
      errors.username = t('register.validation.username_format', { regex })
    } else if (!regex && !new RegExp(DEFAULT_USERNAME_REGEX).test(rawUsername)) {
      errors.username = t('register.validation.username_format', { regex: DEFAULT_USERNAME_REGEX })
    }
  }

  const validateEmail = (): void => {
    errors.email = ''
    if (!form.email) {
      errors.email = t('register.validation.email_required')
    } else if (!EMAIL_REGEX.test(form.email)) {
      errors.email = t('register.validation.email_format')
    }
  }

  const validatePassword = (): void => {
    errors.password = ''
    if (!form.password) {
      errors.password = t('register.validation.password_required')
    } else if (passwordRegex.value && !new RegExp(passwordRegex.value).test(form.password)) {
      errors.password = t('register.validation.password_format', { regex: passwordRegex.value })
    }
  }

  const validateCode = (): void => {
    errors.code = ''
    if (isMethodEnabled('email') && isMethodRequired('email') && !form.code) {
      errors.code = t('register.validation.code_required')
    }
  }

  const validateCaptcha = (): void => {
    errors.captcha = ''
    if (isMethodEnabled('captcha') && isMethodRequired('captcha') && !form.captchaAnswer) {
      errors.captcha = t('register.validation.captcha_required')
    }
  }

  const validateDiscord = (): void => {
    errors.discord = ''
    if (isMethodRequired('discord') && !discordLinked.value) {
      errors.discord = t('discord.required')
    }
  }

  const validatePhone = (): void => {
    errors.phone = ''
    if (isMethodEnabled('sms') && isMethodRequired('sms')) {
      if (!form.phone) {
        errors.phone = t('sms.invalidPhone')
      }
    }
  }

  const validateSmsCode = (): void => {
    errors.smsCode = ''
    if (isMethodEnabled('sms') && isMethodRequired('sms') && !form.smsCode) {
      errors.smsCode = t('register.validation.code_required')
    }
  }

  const validateForm = (): void => {
    validateUsername()
    validateEmail()
    validatePassword()
    validateCode()
    validateCaptcha()
    validateDiscord()
    validatePhone()
    validateSmsCode()
  }

  const clearErrors = (): void => {
    errors.username = ''
    errors.email = ''
    errors.code = ''
    errors.password = ''
    errors.captcha = ''
    errors.discord = ''
    errors.phone = ''
    errors.smsCode = ''
  }

  const hasErrors = computed<boolean>(() => {
    return Object.values(errors).some(error => !!error)
  })

  return {
    form,
    errors,
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
    getUsernameForValidation,
    stripBedrockPrefixes,
    normalizeUsernameForPlatform,
    selectPlatform,
    clearErrors,
    hasErrors
  }
}

export type ValidationField = keyof ValidationErrors

export function createValidationForm(): ValidationForm {
  return {
    username: '',
    email: '',
    code: '',
    password: '',
    captchaAnswer: '',
    phone: '',
    countryCode: '+86',
    smsCode: ''
  }
}

export function createValidationErrors(): ValidationErrors {
  return {
    username: '',
    email: '',
    code: '',
    password: '',
    captcha: '',
    discord: '',
    phone: '',
    smsCode: ''
  }
}
