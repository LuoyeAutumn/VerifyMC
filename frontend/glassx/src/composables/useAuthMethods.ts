import { computed, reactive, type ComputedRef, type Ref } from 'vue'
import type { ConfigResponse, AuthMethodsConfig } from '@/services/api'

export type AuthMethodType = 'email' | 'sms' | 'captcha' | 'discord'

export interface AuthMethodState {
  type: AuthMethodType
  required: boolean
  completed: boolean
  data: Record<string, unknown>
}

export interface AuthMethodsState {
  methods: AuthMethodState[]
  mustMethods: AuthMethodType[]
  optionalMethods: AuthMethodType[]
  minOptionalRequired: number
}

export interface UseAuthMethodsOptions {
  config: Ref<ConfigResponse>
}

export interface UseAuthMethodsReturn {
  authState: AuthMethodsState
  isMethodEnabled: (type: AuthMethodType) => boolean
  isMethodRequired: (type: AuthMethodType) => boolean
  setMethodCompleted: (type: AuthMethodType, completed: boolean, data?: Record<string, unknown>) => void
  getMethodData: <T extends Record<string, unknown>>(type: AuthMethodType) => T | null
  verificationProgress: ComputedRef<{ completed: number; total: number; percentage: number }>
  canSubmit: ComputedRef<boolean>
  getMissingRequiredMethods: () => AuthMethodType[]
  getOptionalMethodsProgress: () => { completed: number; required: number }
  resetMethod: (type: AuthMethodType) => void
  resetAll: () => void
}

export function useAuthMethods(options: UseAuthMethodsOptions): UseAuthMethodsReturn {
  const { config } = options

  const methodStates = reactive<Map<AuthMethodType, AuthMethodState>>(new Map())

  const authMethodsConfig = computed<AuthMethodsConfig | null>(() => config.value.authMethodsConfig || null)

  const mustMethods = computed<AuthMethodType[]>(() => {
    const cfg = authMethodsConfig.value
    if (cfg?.mustAuthMethods && Array.isArray(cfg.mustAuthMethods)) {
      return cfg.mustAuthMethods.filter((m): m is AuthMethodType =>
        ['email', 'sms', 'captcha', 'discord'].includes(m)
      )
    }
    const methods: AuthMethodType[] = []
    if (config.value.captcha?.emailEnabled !== false) {
      methods.push('email')
    }
    if (config.value.captcha?.enabled) {
      methods.push('captcha')
    }
    if (config.value.discord?.required) {
      methods.push('discord')
    }
    return methods
  })

  const optionalMethods = computed<AuthMethodType[]>(() => {
    const cfg = authMethodsConfig.value
    if (cfg?.optionAuthMethods && Array.isArray(cfg.optionAuthMethods)) {
      return cfg.optionAuthMethods.filter((m): m is AuthMethodType =>
        ['email', 'sms', 'captcha', 'discord'].includes(m)
      )
    }
    const methods: AuthMethodType[] = []
    if (config.value.discord?.enabled && !config.value.discord?.required) {
      methods.push('discord')
    }
    if (config.value.sms?.enabled) {
      methods.push('sms')
    }
    return methods
  })

  const minOptionalRequired = computed<number>(() => {
    return authMethodsConfig.value?.minOptionAuthMethods ?? 0
  })

  const isMethodEnabled = (type: AuthMethodType): boolean => {
    const cfg = authMethodsConfig.value
    if (cfg && (cfg.mustAuthMethods?.length > 0 || cfg.optionAuthMethods?.length > 0)) {
      return cfg.mustAuthMethods?.includes(type) || cfg.optionAuthMethods?.includes(type) || false
    }
    switch (type) {
      case 'email':
        return config.value.captcha?.emailEnabled !== false
      case 'sms':
        return config.value.sms?.enabled === true
      case 'captcha':
        return config.value.captcha?.enabled === true
      case 'discord':
        return config.value.discord?.enabled === true
      default:
        return false
    }
  }

  const isMethodRequired = (type: AuthMethodType): boolean => {
    return mustMethods.value.includes(type)
  }

  const getOrCreateMethodState = (type: AuthMethodType): AuthMethodState => {
    if (!methodStates.has(type)) {
      methodStates.set(type, {
        type,
        required: isMethodRequired(type),
        completed: false,
        data: {}
      })
    }
    const state = methodStates.get(type)!
    state.required = isMethodRequired(type)
    return state
  }

  const setMethodCompleted = (
    type: AuthMethodType,
    completed: boolean,
    data?: Record<string, unknown>
  ): void => {
    const state = getOrCreateMethodState(type)
    state.completed = completed
    if (data) {
      state.data = { ...state.data, ...data }
    }
  }

  const getMethodData = <T extends Record<string, unknown>>(type: AuthMethodType): T | null => {
    const state = methodStates.get(type)
    return state?.data as T | null
  }

  const resetMethod = (type: AuthMethodType): void => {
    const state = methodStates.get(type)
    if (state) {
      state.completed = false
      state.data = {}
    }
  }

  const resetAll = (): void => {
    methodStates.forEach((state) => {
      state.completed = false
      state.data = {}
    })
  }

  const verificationProgress = computed(() => {
    const allMethods = [...mustMethods.value, ...optionalMethods.value]
    const enabledMethods = allMethods.filter(isMethodEnabled)
    const uniqueMethods = [...new Set(enabledMethods)]

    let completed = 0
    for (const method of uniqueMethods) {
      const state = methodStates.get(method)
      if (state?.completed) {
        completed++
      }
    }

    const total = uniqueMethods.length
    const percentage = total > 0 ? Math.round((completed / total) * 100) : 0

    return { completed, total, percentage }
  })

  const getMissingRequiredMethods = (): AuthMethodType[] => {
    const missing: AuthMethodType[] = []
    for (const method of mustMethods.value) {
      if (!isMethodEnabled(method)) continue
      const state = methodStates.get(method)
      if (!state?.completed) {
        missing.push(method)
      }
    }
    return missing
  }

  const getOptionalMethodsProgress = (): { completed: number; required: number } => {
    let completed = 0
    for (const method of optionalMethods.value) {
      if (!isMethodEnabled(method)) continue
      const state = methodStates.get(method)
      if (state?.completed) {
        completed++
      }
    }
    return {
      completed,
      required: minOptionalRequired.value
    }
  }

  const canSubmit = computed(() => {
    const missingRequired = getMissingRequiredMethods()
    if (missingRequired.length > 0) {
      return false
    }

    if (minOptionalRequired.value > 0) {
      const optionalProgress = getOptionalMethodsProgress()
      if (optionalProgress.completed < minOptionalRequired.value) {
        return false
      }
    }

    return true
  })

  const authState: AuthMethodsState = reactive({
    get methods() {
      return Array.from(methodStates.values())
    },
    get mustMethods() {
      return mustMethods.value
    },
    get optionalMethods() {
      return optionalMethods.value
    },
    get minOptionalRequired() {
      return minOptionalRequired.value
    }
  })

  return {
    authState,
    isMethodEnabled,
    isMethodRequired,
    setMethodCompleted,
    getMethodData,
    verificationProgress,
    canSubmit,
    getMissingRequiredMethods,
    getOptionalMethodsProgress,
    resetMethod,
    resetAll
  }
}
