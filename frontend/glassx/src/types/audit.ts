import type { PaginationState } from './common'

export interface AuditLogItem {
  id?: number
  action: string
  operator: string
  target: string
  detail: string
  occurredAt: number
}

export interface AuditLogQuery {
  page?: number
  size?: number
  action?: string
  keyword?: string
}

export interface AuditLogsResponse {
  success: boolean
  items: AuditLogItem[]
  availableActions: string[]
  pagination: PaginationState
  message?: string
}
