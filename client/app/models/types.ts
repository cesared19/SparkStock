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

export interface SparkyTCProject {
	projectShortCode: string
	title: string
	deviceMode?: string
	// newest first: the first one is the current build
	builds: string[]
}

export interface KnownModel {
	model: string
	version?: string
}

export interface CoverageTarget {
	id: number
	projectCode: string
	model: string
	version?: string
}

export type CoverageState = 'PASSING' | 'FAILING' | 'MISSING'

export interface CoverageRow {
	model: string
	version?: string
	target?: CoverageTarget
	state: CoverageState
	passed: number
	failed: number
	blocked: number
	skipped: number
	devices: string[]
}

export interface CoverageMatrix {
	projectCode: string
	build?: string
	rows: CoverageRow[]
	covered: number
	targets: number
}

export type AlertLevel = 'ATTENTION' | 'OVERDUE' | 'VERY_OVERDUE'

export type ReportPeriod = 'D30' | 'D90' | 'D180' | 'D365' | 'ALL'

export interface DeviceRef {
	id: string
	assetTag: string
	name: string
	model: string
	type: string
}

export interface Report {
	period: ReportPeriod
	from: Timestamp
	to: Timestamp
	stock: {
		total: number
		retired: number
		byStatus: Record<Exclude<DeviceStatus, 'RETIRED'>, number>
		byType: { typeId: number; name: string; usage: DeviceUsage; total: number; byStatus: Record<string, number> }[]
		byLocation: { name?: string; count: number }[]
	}
	loans: {
		started: number
		returned: number
		returnedLate: number
		activeNow: number
		totalHours: number
		averageHours?: number
		medianHours?: number
		averagePlannedHours?: number
		longest?: { device: DeviceRef; person?: Person; hours: number; active: boolean }
		byDevice: { device: DeviceRef; loans: number; hours: number }[]
		byPerson: { person: Person; loans: number; hours: number; returnedLate: number }[]
	}
	usage: {
		devices: {
			device: DeviceRef
			utilization: number
			hours: number
			loans: number
			lastLoanAt?: Timestamp
			unused: boolean
			status: DeviceStatus
		}[]
		averageUtilization?: number
		used: number
		unused: number
	}
	alerts: {
		alerts: {
			device: DeviceRef
			holder?: Person
			level: AlertLevel
			heldSince: Timestamp
			dueAt: Timestamp
			heldHours: number
			plannedHours: number
			ratio: number
			overdueHours: number
		}[]
		attention: number
		overdue: number
		veryOverdue: number
		escalateAfterDays: number
	}
}
