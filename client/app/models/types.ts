export type UserRole = 'ADMIN' | 'TESTER'
export type DeviceUsage = 'POOL' | 'ASSIGNED'
export type DeviceStatus =
	| 'AVAILABLE'
	| 'ON_LOAN'
	| 'ASSIGNED'
	| 'IN_REPAIR'
	| 'LOST'
	| 'RETIRED'
export type FieldKind = 'TEXT' | 'NUMBER' | 'DATE' | 'BOOLEAN' | 'SELECT'
export type DeviceEventKind =
	| 'CREATED'
	| 'UPDATED'
	| 'CHECKED_OUT'
	| 'CHECKED_IN'
	| 'ASSIGNED'
	| 'UNASSIGNED'
	| 'STATUS_CHANGED'
	| 'IMPORTED'

export interface DataResponse<T> {
	status: 'SUCCESS'
	data: T
}

export interface User {
	userID: string
	username: string
	displayName: string
	role: UserRole
	isActive: boolean
}

export interface Person {
	userID: string
	displayName: string
	username: string
}

export interface Location {
	id: number
	name: string
	isArchived: boolean
}

export interface FieldDefinition {
	key: string
	label: string
	kind: FieldKind
	options?: string[]
	required: boolean
}

export interface DeviceType {
	id: number
	name: string
	tagPrefix: string
	usage: DeviceUsage
	fields: FieldDefinition[]
	coverageField?: string
	isArchived: boolean
}

/** Dates from the backend are seconds since the epoch (with a fraction). */
export type Timestamp = number

export interface Device {
	id: string
	assetTag: string
	type: DeviceType
	name: string
	manufacturer?: string
	model: string
	serialNumber?: string
	status: DeviceStatus
	location?: Location
	holder?: Person
	heldSince?: Timestamp
	dueAt?: Timestamp
	isOverdue: boolean
	purchaseDate?: string
	warrantyEnd?: string
	notes?: string
	projectCode?: string
	externalId?: string
	fields: Record<string, string | number | boolean>
	coverageVersion?: string
	createdAt: Timestamp
	updatedAt: Timestamp
}

export interface DeviceEvent {
	id: number
	kind: DeviceEventKind
	actor?: Person
	holder?: Person
	fromStatus?: DeviceStatus
	toStatus?: DeviceStatus
	note?: string
	details?: Record<string, unknown>
	createdAt: Timestamp
}

export interface DeviceDetail {
	device: Device
	events: DeviceEvent[]
}

export interface Dashboard {
	counts: Record<DeviceStatus, number>
	total: number
	myDevices: Device[]
	overdue: Device[]
	warrantyEnding: Device[]
	loanDefaultDays: number
}

export interface LoginResponse {
	token: string
	expiresAt: Timestamp
	user: User
}
