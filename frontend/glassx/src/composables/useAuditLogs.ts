import { onScopeDispose, ref, watch } from 'vue'
import { apiService } from '@/services/api'
import type { AuditLogItem } from '@/types'

interface UseAuditLogsOptions {
  t: (key: string) => string
  notification: {
    error: (title: string, message?: string) => void
  }
}

export const useAuditLogs = ({ t, notification }: UseAuditLogsOptions) => {
  const loading = ref(false)
  const items = ref<AuditLogItem[]>([])
  const availableActions = ref<string[]>([])
  const keyword = ref('')
  const actionFilter = ref('')

  const currentPage = ref(1)
  const pageSize = ref(20)
  const totalCount = ref(0)
  const totalPages = ref(0)
  const hasNext = ref(false)
  const hasPrev = ref(false)

  let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null

  const applyResponse = (response: {
    items?: AuditLogItem[]
    availableActions?: string[]
    pagination?: {
      currentPage?: number
      totalCount?: number
      totalPages?: number
      hasNext?: boolean
      hasPrev?: boolean
    }
  }) => {
    items.value = response.items ?? []
    availableActions.value = response.availableActions ?? []
    currentPage.value = response.pagination?.currentPage ?? 1
    totalCount.value = response.pagination?.totalCount ?? 0
    totalPages.value = response.pagination?.totalPages ?? 0
    hasNext.value = response.pagination?.hasNext ?? false
    hasPrev.value = response.pagination?.hasPrev ?? false
  }

  const loadAuditLogs = async () => {
    loading.value = true

    try {
      const response = await apiService.getAuditLogs({
        page: currentPage.value,
        size: pageSize.value,
        action: actionFilter.value,
        keyword: keyword.value,
      })

      if (!response.success) {
        notification.error(t('common.error'), response.message || t('dashboard.audit_log.load_error'))
        applyResponse({})
        return
      }

      applyResponse(response)
    } catch (error) {
      applyResponse({})
      notification.error(
        t('common.error'),
        error instanceof Error ? error.message : t('dashboard.audit_log.load_error'),
      )
    } finally {
      loading.value = false
    }
  }

  const handlePageChange = (page: number) => {
    currentPage.value = page
    loadAuditLogs()
  }

  const handlePageSizeChange = (size: number) => {
    pageSize.value = size
    currentPage.value = 1
    loadAuditLogs()
  }

  const stopActionWatch = watch(actionFilter, () => {
    currentPage.value = 1
    loadAuditLogs()
  })

  const stopKeywordWatch = watch(keyword, () => {
    if (searchDebounceTimer) {
      clearTimeout(searchDebounceTimer)
    }

    searchDebounceTimer = setTimeout(() => {
      currentPage.value = 1
      loadAuditLogs()
    }, 400)
  })

  onScopeDispose(() => {
    stopActionWatch()
    stopKeywordWatch()
    if (searchDebounceTimer) {
      clearTimeout(searchDebounceTimer)
    }
  })

  return {
    loading,
    items,
    availableActions,
    keyword,
    actionFilter,
    currentPage,
    pageSize,
    totalCount,
    totalPages,
    hasNext,
    hasPrev,
    loadAuditLogs,
    handlePageChange,
    handlePageSizeChange,
  }
}
