import type { DeviceEventKind, DeviceStatus, Timestamp } from '~/models/types'

export const STATUS_LABEL: Record<DeviceStatus, string> = {
	AVAILABLE: 'Available',
	ON_LOAN: 'On loan',
	ASSIGNED: 'Assigned',
	IN_REPAIR: 'In repair',
	LOST: 'Lost',
	RETIRED: 'Retired',
}

export const STATUS_STYLE: Record<DeviceStatus, string> = {
	AVAILABLE: 'bg-green-100 text-green-800',
	ON_LOAN: 'bg-blue-100 text-blue-800',
	ASSIGNED: 'bg-indigo-100 text-indigo-800',
	IN_REPAIR: 'bg-yellow-100 text-yellow-800',
	LOST: 'bg-red-100 text-red-800',
	RETIRED: 'bg-gray-100 text-gray-600',
}

export const EVENT_LABEL: Record<DeviceEventKind, string> = {
	CREATED: 'Added',
	UPDATED: 'Edited',
	CHECKED_OUT: 'Checked out',
	CHECKED_IN: 'Returned',
	ASSIGNED: 'Assigned',
	UNASSIGNED: 'Unassigned',
	STATUS_CHANGED: 'Status changed',
	IMPORTED: 'Imported',
}

export function toDate(timestamp: Timestamp) {
	return new Date(timestamp * 1000)
}

export function formatDate(value: Timestamp | string | undefined) {
	if (value === undefined || value === null) return ''
	const date =
		typeof value === 'number' ? toDate(value) : new Date(`${value}T00:00:00`)
	return date.toLocaleDateString(undefined, {
		year: 'numeric',
		month: 'short',
		day: 'numeric',
	})
}

export function formatDateTime(value: Timestamp) {
	return toDate(value).toLocaleString(undefined, {
		year: 'numeric',
		month: 'short',
		day: 'numeric',
		hour: '2-digit',
		minute: '2-digit',
	})
}

/** yyyy-mm-dd in the browser's time zone, for date inputs. */
export function dateInputValue(date: Date) {
	const pad = (n: number) => String(n).padStart(2, '0')
	return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}
