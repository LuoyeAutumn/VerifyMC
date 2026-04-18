import { beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'
import { useAuditLogs } from './useAuditLogs'

const { mockApiService } = vi.hoisted(() => ({
  mockApiService: {
    getAuditLogs: vi.fn(),
  },
}))

vi.mock('@/services/api', () => ({
  apiService: mockApiService,
}))

describe('useAuditLogs', () => {
  const notification = { error: vi.fn() }
  const t = (key: string) => key

  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  it('loads paginated audit logs from the api', async () => {
    mockApiService.getAuditLogs.mockResolvedValueOnce({
      success: true,
      items: [
        {
          id: 1,
          action: 'approve',
          operator: 'admin',
          target: 'alice',
          detail: '',
          occurredAt: 123,
        },
      ],
      availableActions: ['approve', 'reject'],
      pagination: {
        currentPage: 2,
        totalCount: 15,
        totalPages: 2,
        hasNext: false,
        hasPrev: true,
      },
    })

    const scope = effectScope()
    const composable = scope.run(() => useAuditLogs({ t, notification }))!
    composable.currentPage.value = 2

    await composable.loadAuditLogs()

    expect(mockApiService.getAuditLogs).toHaveBeenCalledWith({
      page: 2,
      size: 20,
      action: '',
      keyword: '',
    })
    expect(composable.items.value).toHaveLength(1)
    expect(composable.availableActions.value).toEqual(['approve', 'reject'])
    expect(composable.totalCount.value).toBe(15)
    expect(composable.hasPrev.value).toBe(true)
    scope.stop()
  })

  it('debounces keyword changes and resets the page before querying', async () => {
    mockApiService.getAuditLogs.mockResolvedValue({
      success: true,
      items: [],
      availableActions: [],
      pagination: {
        currentPage: 1,
        totalCount: 0,
        totalPages: 0,
        hasNext: false,
        hasPrev: false,
      },
    })

    const scope = effectScope()
    const composable = scope.run(() => useAuditLogs({ t, notification }))!
    composable.currentPage.value = 3
    composable.keyword.value = 'alice'

    await Promise.resolve()
    vi.advanceTimersByTime(400)
    await Promise.resolve()

    expect(composable.currentPage.value).toBe(1)
    expect(mockApiService.getAuditLogs).toHaveBeenCalledWith({
      page: 1,
      size: 20,
      action: '',
      keyword: 'alice',
    })
    scope.stop()
  })
})
