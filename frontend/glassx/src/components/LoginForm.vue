<template>
  <div class="flex flex-col gap-6">
    <Card v-if="!showForgotPassword && !showAccountSelection">
      <CardHeader>
        <CardTitle class="text-2xl">{{ $t('login.title') }}</CardTitle>
        <CardDescription>{{ $t('login.subtitle') }}</CardDescription>
      </CardHeader>
      <CardContent>
        <form @submit.prevent="handleSubmit">
          <div class="flex flex-col gap-6">
            <div v-if="showLoginMethodSelector" class="grid gap-2">
              <Label>{{ $t('login.form.login_method') }}</Label>
              <div class="inline-flex w-full rounded-lg bg-white/5 border border-white/10 p-1 gap-1" role="radiogroup">
                <Button
                  v-for="method in availableLoginMethods"
                  :key="method.key"
                  type="button"
                  variant="outline"
                  class="flex-1 border-transparent focus:ring-offset-0"
                  :class="form.loginMethod === method.key ? 'bg-white/20 text-white shadow-sm hover:bg-white/20' : 'text-white/60 hover:bg-white/5 hover:text-white'"
                  @click="form.loginMethod = method.key"
                >
                  {{ method.label }}
                </Button>
              </div>
            </div>

            <div class="grid gap-2">
              <Label for="account">{{ accountLabel }}</Label>
              <div v-if="form.loginMethod === 'phone'" class="flex gap-2">
                <select
                  v-model="form.countryCode"
                  class="h-10 px-3 rounded-md border border-white/10 bg-white/5 text-white text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option v-for="code in countryCodes" :key="code" :value="code">
                    {{ code }}
                  </option>
                </select>
                <Input
                  id="account"
                  type="tel"
                  :placeholder="accountPlaceholder"
                  v-model="form.account"
                  class="flex-1"
                />
              </div>
              <Input
                v-else
                id="account"
                :type="form.loginMethod === 'email' ? 'email' : 'text'"
                :placeholder="accountPlaceholder"
                v-model="form.account"
              />
            </div>

            <div v-if="showVerifyMethodSelector" class="grid gap-2">
              <Label>{{ $t('login.form.verify_method') }}</Label>
              <div class="inline-flex w-full rounded-lg bg-white/5 border border-white/10 p-1 gap-1" role="radiogroup">
                <Button
                  v-for="method in allowedVerifyMethods"
                  :key="method"
                  type="button"
                  variant="outline"
                  class="flex-1 border-transparent focus:ring-offset-0"
                  :class="form.verifyMethod === method ? 'bg-white/20 text-white shadow-sm hover:bg-white/20' : 'text-white/60 hover:bg-white/5 hover:text-white'"
                  @click="form.verifyMethod = method as VerifyMethod"
                >
                  {{ method === 'password' ? $t('login.form.method_password') : $t('login.form.method_code') }}
                </Button>
              </div>
            </div>

            <div v-if="form.verifyMethod === 'password'" class="grid gap-2">
              <Label for="password">{{ $t('login.form.password') }}</Label>
              <Input
                id="password"
                type="password"
                :placeholder="$t('login.form.password_placeholder')"
                v-model="form.password"
              />
            </div>

            <div v-else-if="form.verifyMethod === 'code'" class="grid gap-2">
              <Label for="code">{{ $t('login.form.code') }}</Label>
              <div class="flex gap-2">
                <Input
                  id="code"
                  type="text"
                  :placeholder="$t('login.form.code_placeholder')"
                  v-model="form.code"
                  maxlength="6"
                  class="flex-1"
                />
                <Button
                  type="button"
                  variant="outline"
                  :disabled="sendLoginCodeLoading || loginCooldownSeconds > 0"
                  @click="handleSendLoginCode"
                >
                  <span v-if="sendLoginCodeLoading">{{ $t('register.sending') }}</span>
                  <span v-else-if="loginCooldownSeconds > 0">{{ loginCooldownSeconds }}s</span>
                  <span v-else>{{ $t('login.form.send_code') }}</span>
                </Button>
              </div>
            </div>

            <Button
              type="submit"
              :disabled="loading"
              class="w-full"
            >
              <div v-if="loading" class="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent"></div>
              <span>{{ loading ? $t('common.loading') : $t('login.form.submit') }}</span>
            </Button>

            <div v-if="shouldShowForgotPasswordLink" class="text-center">
              <button
                type="button"
                class="text-sm text-blue-500 hover:text-blue-600 hover:underline"
                @click="showForgotPassword = true"
              >
                {{ $t('login.forgot_password.link') }}
              </button>
            </div>
          </div>
        </form>
      </CardContent>
    </Card>

    <Card v-else-if="showAccountSelection">
      <CardHeader>
        <CardTitle class="text-2xl">{{ $t('login.account_selection.title') }}</CardTitle>
        <CardDescription>{{ $t('login.account_selection.subtitle') }}</CardDescription>
      </CardHeader>
      <CardContent>
        <div class="flex flex-col gap-3">
          <Button
            v-for="account in availableAccounts"
            :key="account.username"
            variant="outline"
            class="w-full justify-start text-left"
            :disabled="selectingAccount"
            @click="handleSelectAccount(account)"
          >
            <div v-if="selectingAccount && selectedAccount === account.username" class="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent"></div>
            <span class="font-medium">{{ account.username }}</span>
          </Button>
        </div>
        <div class="mt-4 text-center">
          <button
            type="button"
            class="text-sm text-blue-500 hover:text-blue-600 hover:underline"
            @click="cancelAccountSelection"
          >
            {{ $t('login.account_selection.back') }}
          </button>
        </div>
      </CardContent>
    </Card>

    <Card v-else>
      <CardHeader>
        <CardTitle class="text-2xl">{{ $t('login.forgot_password.title') }}</CardTitle>
        <CardDescription>{{ $t('login.forgot_password.subtitle') }}</CardDescription>
      </CardHeader>
      <CardContent>
        <form @submit.prevent="handleForgotPassword">
          <div class="flex flex-col gap-6">
            <div v-if="showForgotPasswordMethodSelector" class="grid gap-2">
              <Label>{{ $t('login.forgot_password.verify_method') }}</Label>
              <div class="inline-flex w-full rounded-lg bg-white/5 border border-white/10 p-1 gap-1" role="radiogroup">
                <Button
                  v-for="method in availableForgotPasswordMethods"
                  :key="method.key"
                  type="button"
                  variant="outline"
                  class="flex-1 border-transparent focus:ring-offset-0"
                  :class="forgotForm.verifyMethod === method.key ? 'bg-white/20 text-white shadow-sm hover:bg-white/20' : 'text-white/60 hover:bg-white/5 hover:text-white'"
                  @click="forgotForm.verifyMethod = method.key"
                >
                  {{ method.label }}
                </Button>
              </div>
            </div>

            <div v-if="forgotForm.verifyMethod === 'email'" class="grid gap-2">
              <Label for="forgot-email">{{ $t('login.forgot_password.email') }}</Label>
              <Input
                id="forgot-email"
                type="email"
                :placeholder="$t('login.forgot_password.email_placeholder')"
                v-model="forgotForm.email"
              />
            </div>

            <div v-else class="grid gap-2">
              <Label for="forgot-phone">{{ $t('login.forgot_password.phone') }}</Label>
              <div class="flex gap-2">
                <select
                  v-model="forgotForm.countryCode"
                  class="h-10 px-3 rounded-md border border-white/10 bg-white/5 text-white text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option v-for="code in countryCodes" :key="code" :value="code">
                    {{ code }}
                  </option>
                </select>
                <Input
                  id="forgot-phone"
                  type="tel"
                  :placeholder="$t('login.forgot_password.phone_placeholder')"
                  v-model="forgotForm.phone"
                  class="flex-1"
                />
              </div>
            </div>

            <div class="grid gap-2">
              <Label for="forgot-code">{{ $t('login.forgot_password.code') }}</Label>
              <div class="flex gap-2">
                <Input
                  id="forgot-code"
                  type="text"
                  :placeholder="$t('login.forgot_password.code_placeholder')"
                  v-model="forgotForm.code"
                  maxlength="6"
                  class="flex-1"
                />
                <Button
                  type="button"
                  variant="outline"
                  :disabled="sendCodeLoading || cooldownSeconds > 0"
                  @click="handleSendCode"
                >
                  <span v-if="sendCodeLoading">{{ $t('register.sending') }}</span>
                  <span v-else-if="cooldownSeconds > 0">{{ cooldownSeconds }}s</span>
                  <span v-else>{{ $t('login.forgot_password.send_code') }}</span>
                </Button>
              </div>
            </div>

            <div class="grid gap-2">
              <Label for="forgot-password">{{ $t('login.forgot_password.new_password') }}</Label>
              <Input
                id="forgot-password"
                type="password"
                :placeholder="$t('login.forgot_password.new_password_placeholder')"
                v-model="forgotForm.password"
              />
            </div>

            <Button
              type="submit"
              :disabled="resetLoading"
              class="w-full"
            >
              <div v-if="resetLoading" class="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent"></div>
              <span>{{ resetLoading ? $t('common.loading') : $t('login.forgot_password.reset') }}</span>
            </Button>

            <div class="text-center">
              <button
                type="button"
                class="text-sm text-blue-500 hover:text-blue-600 hover:underline"
                @click="showForgotPassword = false"
              >
                {{ $t('login.forgot_password.back_to_login') }}
              </button>
            </div>
          </div>
        </form>
      </CardContent>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onUnmounted, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useNotification } from '@/composables/useNotification'
import { apiService } from '@/services/api'
import { sessionService } from '@/services/session'
import Card from './ui/Card.vue'
import CardHeader from './ui/CardHeader.vue'
import CardTitle from './ui/CardTitle.vue'
import CardDescription from './ui/CardDescription.vue'
import CardContent from './ui/CardContent.vue'
import Button from './ui/Button.vue'
import Input from './ui/Input.vue'
import Label from './ui/Label.vue'

const { t, locale } = useI18n()
const router = useRouter()
const route = useRoute()
const notification = useNotification()

const loading = ref(false)
const showForgotPassword = ref(false)
const sendCodeLoading = ref(false)
const resetLoading = ref(false)
const cooldownSeconds = ref(0)
const sendLoginCodeLoading = ref(false)
const loginCooldownSeconds = ref(0)
let cooldownTimer: ReturnType<typeof setInterval> | null = null
let loginCooldownTimer: ReturnType<typeof setInterval> | null = null

const redirectTimeout = ref<ReturnType<typeof setTimeout> | null>(null)

const countryCodes = ref<string[]>(['+86', '+1', '+44', '+81', '+82', '+852', '+853', '+886'])
const phoneRegex = ref<string>('')
const passwordRegex = ref<string>('')
const allowedLoginMethods = ref<string[]>(['username', 'email_password', 'email_code', 'phone_password', 'phone_code'])
const allowedLoginIdentifiers = ref<string[]>(['username', 'email', 'phone'])
const forgotPasswordConfig = ref<{ enabled: boolean; resetMethods: string[] }>({
  enabled: true,
  resetMethods: []
})

const verifyMethodsConfig = ref<Record<string, string[]>>({
  username: ['password'],
  email: ['password', 'code'],
  phone: ['password', 'code']
})

type LoginMethod = 'username' | 'email' | 'phone'
type VerifyMethod = 'password' | 'code'

const form = reactive({
  account: '',
  password: '',
  code: '',
  loginMethod: 'username' as LoginMethod,
  verifyMethod: 'password' as VerifyMethod,
  countryCode: '+86'
})

const forgotForm = reactive({
  verifyMethod: 'email' as 'email' | 'phone',
  email: '',
  phone: '',
  countryCode: '+86',
  code: '',
  password: ''
})

const errors = reactive({
  account: '',
  password: ''
})

interface AccountInfo {
  username: string
  email?: string
  status?: string
}

const showAccountSelection = ref(false)
const availableAccounts = ref<AccountInfo[]>([])
const tempToken = ref('')
const selectingAccount = ref(false)
const selectedAccount = ref('')

const showLoginMethodSelector = computed(() => allowedLoginIdentifiers.value.length > 1)

const allowedVerifyMethods = computed(() => {
  const methods: VerifyMethod[] = []
  if (form.loginMethod === 'username') {
    if (allowedLoginMethods.value.includes('username')) {
      methods.push('password')
    }
  } else if (form.loginMethod === 'email') {
    if (allowedLoginMethods.value.includes('email_password')) {
      methods.push('password')
    }
    if (allowedLoginMethods.value.includes('email_code')) {
      methods.push('code')
    }
  } else if (form.loginMethod === 'phone') {
    if (allowedLoginMethods.value.includes('phone_password')) {
      methods.push('password')
    }
    if (allowedLoginMethods.value.includes('phone_code')) {
      methods.push('code')
    }
  }
  return methods
})

const showVerifyMethodSelector = computed(() => allowedVerifyMethods.value.length > 1)

const availableLoginMethods = computed(() => {
  const methods: { key: LoginMethod, label: string }[] = []
  if (allowedLoginIdentifiers.value.includes('username')) {
    methods.push({ key: 'username', label: t('login.form.method_username') })
  }
  if (allowedLoginIdentifiers.value.includes('email')) {
    methods.push({ key: 'email', label: t('login.form.method_email') })
  }
  if (allowedLoginIdentifiers.value.includes('phone')) {
    methods.push({ key: 'phone', label: t('login.form.method_phone') })
  }
  return methods
})

const showForgotPasswordMethodSelector = computed(() => 
  forgotPasswordConfig.value.resetMethods.length > 1
)

const availableForgotPasswordMethods = computed(() => {
  const methods: { key: 'email' | 'phone', label: string }[] = []
  if (forgotPasswordConfig.value.resetMethods.includes('email')) {
    methods.push({ key: 'email', label: t('login.forgot_password.method_email') })
  }
  if (forgotPasswordConfig.value.resetMethods.includes('sms')) {
    methods.push({ key: 'phone', label: t('login.forgot_password.method_phone') })
  }
  return methods
})

const shouldShowForgotPasswordLink = computed(() => 
  forgotPasswordConfig.value.enabled && availableForgotPasswordMethods.value.length > 0
)

const accountLabel = computed(() => {
  switch (form.loginMethod) {
    case 'username':
      return t('login.form.username')
    case 'email':
      return t('login.form.email')
    case 'phone':
      return t('login.form.phone')
    default:
      return t('login.form.username')
  }
})

const accountPlaceholder = computed(() => {
  switch (form.loginMethod) {
    case 'username':
      return t('login.form.username_placeholder')
    case 'email':
      return t('login.form.email_placeholder')
    case 'phone':
      return t('login.form.phone_placeholder')
    default:
      return t('login.form.username_placeholder')
  }
})

const loadConfig = async () => {
  try {
    const config = await apiService.getConfig()
    if (config.login?.allowedMethods) {
      allowedLoginMethods.value = config.login.allowedMethods
    }
    if (config.login?.allowedIdentifiers) {
      allowedLoginIdentifiers.value = config.login.allowedIdentifiers
      if (allowedLoginIdentifiers.value.length > 0 && !allowedLoginIdentifiers.value.includes(form.loginMethod)) {
        form.loginMethod = allowedLoginIdentifiers.value[0] as LoginMethod
      }
    }
    if (allowedVerifyMethods.value.length > 0) {
      form.verifyMethod = allowedVerifyMethods.value[0] as VerifyMethod
    }
    if (config.sms?.countryCodes) {
      countryCodes.value = config.sms.countryCodes
    }
    if (config.sms?.phoneRegex) {
      phoneRegex.value = config.sms.phoneRegex
    }
    if (config.forgotPassword) {
      forgotPasswordConfig.value = {
        enabled: config.forgotPassword.enabled ?? true,
        resetMethods: config.forgotPassword.resetMethods ?? []
      }
    }
    if (config.authme?.passwordRegex) {
      passwordRegex.value = config.authme.passwordRegex
    }
  } catch {
    // 使用默认值
  }
}

watch(() => form.loginMethod, () => {
  const methods = verifyMethodsConfig.value[form.loginMethod] || ['password']
  if (methods.length > 0) {
    form.verifyMethod = methods[0] as VerifyMethod
  }
})

onMounted(() => {
  loadConfig()
})

const validateForm = () => {
  errors.account = ''
  errors.password = ''
  
  const account = form.account.trim()
  
  let isValid = true
  
  if (!account) {
    errors.account = t('login.validation.account_required')
    isValid = false
  } else if (form.loginMethod === 'email') {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    if (!emailRegex.test(account)) {
      errors.account = t('register.validation.email_format')
      isValid = false
    }
  } else if (form.loginMethod === 'phone') {
    const regex = phoneRegex.value ? new RegExp(phoneRegex.value) : /^\d{6,15}$/
    if (!regex.test(account)) {
      errors.account = t('sms.invalidPhone')
      isValid = false
    }
  }
  
  if (form.verifyMethod === 'password') {
    if (!form.password.trim()) {
      errors.password = t('login.validation.password_required')
      isValid = false
    }
  } else if (form.verifyMethod === 'code') {
    if (!form.code.trim() || form.code.length !== 6) {
      errors.password = t('register.validation.code_required')
      isValid = false
    }
  }
  
  return isValid
}

const handleSubmit = async () => {
  if (!validateForm()) {
    const firstError = errors.account || errors.password
    if (firstError) {
      notification.error(firstError)
    }
    return
  }

  loading.value = true

  try {
    const account = form.loginMethod === 'phone' 
      ? form.countryCode + form.account.trim()
      : form.account.trim()

    const request: {
      account: string
      password: string
      loginMethod: 'username' | 'email' | 'phone'
      language: string
      verifyMethod?: 'password' | 'code'
      code?: string
      countryCode?: string
    } = {
      account,
      password: form.password,
      loginMethod: form.loginMethod,
      language: locale.value
    }

    if (form.loginMethod === 'email' || form.loginMethod === 'phone') {
      request.verifyMethod = form.verifyMethod
      if (form.verifyMethod === 'code') {
        request.code = form.code.trim()
        request.password = ''
      }
    }

    if (form.loginMethod === 'phone') {
      request.countryCode = form.countryCode
    }

    const response = await apiService.adminLogin(request)
    
    if (response.success) {
      if (response.requireAccountSelection && response.accounts && response.tempToken) {
        availableAccounts.value = response.accounts.map(username => ({ username }))
        tempToken.value = response.tempToken
        showAccountSelection.value = true
        return
      }

      sessionService.setToken(response.token!)
      
      sessionService.setUserInfo({
        username: response.username || form.account.trim(),
        isAdmin: response.isAdmin ?? false
      })
      
      notification.success(response.message || t('login.messages.success'))
      const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
        ? route.query.redirect
        : '/dashboard'

      redirectTimeout.value = setTimeout(() => {
        router.push(redirect)
      }, 1000)
    } else {
      notification.error(response.message || t('login.messages.error'))
    }
    
  } catch {
    notification.error(t('login.messages.invalid_credentials'))
  } finally {
    loading.value = false
  }
}

const handleSelectAccount = async (account: AccountInfo) => {
  selectingAccount.value = true
  selectedAccount.value = account.username

  try {
    const response = await apiService.adminLogin({
      account: form.loginMethod === 'phone' 
        ? form.countryCode + form.account.trim()
        : form.account.trim(),
      password: form.password,
      loginMethod: form.loginMethod,
      selectedUsername: account.username,
      tempToken: tempToken.value,
      language: locale.value
    })

    if (response.success && response.token) {
      sessionService.setToken(response.token)
      
      sessionService.setUserInfo({
        username: response.username || account.username,
        isAdmin: response.isAdmin ?? false
      })
      
      notification.success(response.message || t('login.messages.success'))
      
      const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
        ? route.query.redirect
        : '/dashboard'

      redirectTimeout.value = setTimeout(() => {
        router.push(redirect)
      }, 1000)
    } else {
      notification.error(response.message || t('login.messages.error'))
    }
  } catch (error) {
    if (error instanceof TypeError && error.message.includes('fetch')) {
      notification.error(t('login.messages.network_error'))
    } else if (error instanceof Error) {
      const errorMessage = error.message || t('login.messages.invalid_credentials')
      notification.error(errorMessage)
    } else {
      notification.error(t('login.messages.invalid_credentials'))
    }
  } finally {
    selectingAccount.value = false
    selectedAccount.value = ''
  }
}

const cancelAccountSelection = () => {
  showAccountSelection.value = false
  availableAccounts.value = []
  tempToken.value = ''
}

const startCooldown = (seconds: number) => {
  cooldownSeconds.value = seconds
  if (cooldownTimer) {
    clearInterval(cooldownTimer)
  }
  cooldownTimer = setInterval(() => {
    if (cooldownSeconds.value > 0) {
      cooldownSeconds.value--
    } else {
      if (cooldownTimer) {
        clearInterval(cooldownTimer)
        cooldownTimer = null
      }
    }
  }, 1000)
}

const startLoginCooldown = (seconds: number) => {
  loginCooldownSeconds.value = seconds
  if (loginCooldownTimer) {
    clearInterval(loginCooldownTimer)
  }
  loginCooldownTimer = setInterval(() => {
    if (loginCooldownSeconds.value > 0) {
      loginCooldownSeconds.value--
    } else {
      if (loginCooldownTimer) {
        clearInterval(loginCooldownTimer)
        loginCooldownTimer = null
      }
    }
  }, 1000)
}

const handleSendLoginCode = async () => {
  if (!form.account.trim()) {
    notification.error(t('login.validation.account_required'))
    return
  }

  if (form.loginMethod === 'email') {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    if (!emailRegex.test(form.account.trim())) {
      notification.error(t('register.validation.email_format'))
      return
    }
  } else if (form.loginMethod === 'phone') {
    const regex = phoneRegex.value ? new RegExp(phoneRegex.value) : /^\d{6,15}$/
    if (!regex.test(form.account.trim())) {
      notification.error(t('sms.invalidPhone'))
      return
    }
  }

  sendLoginCodeLoading.value = true

  try {
    const response = await apiService.sendLoginCode({
      account: form.loginMethod === 'phone' 
        ? form.countryCode + form.account.trim()
        : form.account.trim(),
      loginMethod: form.loginMethod as 'email' | 'phone',
      countryCode: form.loginMethod === 'phone' ? form.countryCode : undefined,
      language: locale.value
    })

    if (response.success) {
      notification.success(t('login.code_sent'))
      if (response.remainingSeconds) {
        startLoginCooldown(response.remainingSeconds)
      } else {
        startLoginCooldown(60)
      }
    } else {
      notification.error(response.message || t('register.sendFailed'))
      if (response.remainingSeconds) {
        startLoginCooldown(response.remainingSeconds)
      }
    }
  } catch {
    notification.error(t('register.sendFailed'))
  } finally {
    sendLoginCodeLoading.value = false
  }
}

const handleSendCode = async () => {
  if (forgotForm.verifyMethod === 'email') {
    if (!forgotForm.email.trim()) {
      notification.error(t('register.validation.email_required'))
      return
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    if (!emailRegex.test(forgotForm.email.trim())) {
      notification.error(t('register.validation.email_format'))
      return
    }

    sendCodeLoading.value = true

    try {
      const response = await apiService.forgotPasswordSendCode({
        account: forgotForm.email.trim(),
        language: locale.value
      })

      if (response.success) {
        notification.success(t('login.forgot_password.code_sent'))
        if (response.remainingSeconds) {
          startCooldown(response.remainingSeconds)
        } else {
          startCooldown(60)
        }
      } else {
        notification.error(response.message || t('register.sendFailed'))
        if (response.remainingSeconds) {
          startCooldown(response.remainingSeconds)
        }
      }
    } catch {
      notification.error(t('register.sendFailed'))
    } finally {
      sendCodeLoading.value = false
    }
  } else {
    if (!forgotForm.phone.trim()) {
      notification.error(t('login.forgot_password.phone_required'))
      return
    }

    const regex = phoneRegex.value ? new RegExp(phoneRegex.value) : /^\d{6,15}$/
    if (!regex.test(forgotForm.phone.trim())) {
      notification.error(t('sms.invalidPhone'))
      return
    }

    sendCodeLoading.value = true

    try {
      const response = await apiService.forgotPasswordSendCode({
        account: forgotForm.phone.trim(),
        countryCode: forgotForm.countryCode,
        language: locale.value
      })

      if (response.success) {
        notification.success(t('sms.sent'))
        if (response.remainingSeconds) {
          startCooldown(response.remainingSeconds)
        } else {
          startCooldown(60)
        }
      } else {
        notification.error(response.message || t('sms.failed'))
        if (response.remainingSeconds) {
          startCooldown(response.remainingSeconds)
        }
      }
    } catch {
      notification.error(t('sms.failed'))
    } finally {
      sendCodeLoading.value = false
    }
  }
}

const handleForgotPassword = async () => {
  let account = ''
  
  if (forgotForm.verifyMethod === 'email') {
    if (!forgotForm.email.trim()) {
      notification.error(t('register.validation.email_required'))
      return
    }
    account = forgotForm.email.trim()
  } else {
    if (!forgotForm.phone.trim()) {
      notification.error(t('login.forgot_password.phone_required'))
      return
    }
    account = forgotForm.countryCode + forgotForm.phone.trim()
  }

  if (!forgotForm.code.trim() || forgotForm.code.length !== 6) {
    notification.error(t('register.validation.code_required'))
    return
  }

  if (!forgotForm.password.trim()) {
    notification.error(t('register.validation.password_required'))
    return
  }

  if (passwordRegex.value) {
    const regex = new RegExp(passwordRegex.value)
    if (!regex.test(forgotForm.password)) {
      notification.error(t('register.validation.password_format'))
      return
    }
  }

  resetLoading.value = true

  try {
    const response = await apiService.forgotPasswordReset({
      account,
      code: forgotForm.code.trim(),
      password: forgotForm.password,
      language: locale.value
    })

    if (response.success) {
      notification.success(t('login.forgot_password.reset_success'))
      forgotForm.email = ''
      forgotForm.phone = ''
      forgotForm.code = ''
      forgotForm.password = ''
      showForgotPassword.value = false
    } else {
      notification.error(response.message || t('login.forgot_password.reset_failed'))
    }
  } catch (error) {
    if (error instanceof TypeError && error.message.includes('fetch')) {
      notification.error(t('login.messages.network_error'))
    } else if (error instanceof Error) {
      const errorMessage = error.message || t('login.forgot_password.reset_failed')
      notification.error(errorMessage)
    } else {
      notification.error(t('login.forgot_password.reset_failed'))
    }
  } finally {
    resetLoading.value = false
  }
}

onUnmounted(() => {
  if (redirectTimeout.value) {
    clearTimeout(redirectTimeout.value)
    redirectTimeout.value = null
  }
  if (cooldownTimer) {
    clearInterval(cooldownTimer)
    cooldownTimer = null
  }
  if (loginCooldownTimer) {
    clearInterval(loginCooldownTimer)
    loginCooldownTimer = null
  }
  showAccountSelection.value = false
  availableAccounts.value = []
  tempToken.value = ''
  selectingAccount.value = false
  selectedAccount.value = ''
})
</script>
