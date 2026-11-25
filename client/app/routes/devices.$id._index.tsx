import {
	type ActionFunctionArgs,
	type LoaderFunctionArgs,
} from '@remix-run/node'
import {
	Form,
	Link,
	useActionData,
	useLoaderData,
	useNavigation,
} from '@remix-run/react'
import QRCode from 'qrcode'
import StatusBadge from '~/components/StatusBadge'
import type { DeviceDetail, DeviceStatus, Location, User } from '~/models/types'
import { attempt, requireAdmin, requireUser } from '~/utility/API.server'
import {
	dateInputValue,
	EVENT_LABEL,
	formatDate,
	formatDateTime,
	STATUS_LABEL,
} from '~/utility/Format'
import { deviceLink } from '~/utility/PublicURL.server'

export async function loader({ request, params }: LoaderFunctionArgs) {
	const { api, user } = await requireUser(request)
	const detail = await api.get<DeviceDetail>(`/devices/${params.id}`)
	const isAdmin = user.role === 'ADMIN'
	const [locations, users] = await Promise.all([
		api.get<Location[]>('/locations'),
		isAdmin && detail.device.type.usage === 'ASSIGNED'
			? api.get<User[]>('/users')
			: Promise.resolve([] as User[]),
	])
	const link = deviceLink(request, detail.device.assetTag)
	const qr = await QRCode.toDataURL(link, { margin: 1, width: 240 })
	const loanDays = Number(process.env.LOAN_DEFAULT_DAYS ?? 7) || 7
	return {
		...detail,
		user,
		locations,
		users: users.filter((u) => u.isActive),
		link,
		qr,
		loanDays,
	}
}

/** A date picked in the form means "until the end of that day" in the server's time zone (TZ). */
function endOfDay(value: FormDataEntryValue | null) {
	const text = String(value ?? '')
	if (!text) return undefined
	return Math.floor(new Date(`${text}T23:59:00`).getTime() / 1000)
}

export async function action({ request, params }: ActionFunctionArgs) {
	const form = await request.formData()
	const intent = String(form.get('intent'))
	const note = String(form.get('note') ?? '') || undefined
	const locationId = form.get('locationId')
		? Number(form.get('locationId'))
		: undefined
	const path = `/devices/${params.id}`

	if (intent === 'check-out' || intent === 'check-in') {
		const { api } = await requireUser(request)
		const body =
			intent === 'check-out'
				? { dueAt: endOfDay(form.get('dueAt')), note }
				: { locationId, note }
		return attempt(() =>
			api
				.post<DeviceDetail>(`${path}/${intent}`, body)
				.then(() => ({ done: intent })),
		)
	}

	const { api } = await requireAdmin(request)
	switch (intent) {
		case 'assign':
			return attempt(() =>
				api
					.post(`${path}/assign`, { userId: form.get('userId') || null, note })
					.then(() => ({ done: intent })),
			)
		case 'unassign':
			return attempt(() =>
				api
					.post(`${path}/unassign`, { locationId, note })
					.then(() => ({ done: intent })),
			)
		case 'status':
			return attempt(() =>
				api
					.post(`${path}/status`, { status: form.get('status'), note })
					.then(() => ({ done: intent })),
			)
		default:
			throw new Response('Unknown action', { status: 400 })
	}
}

function Detail({
	label,
	children,
}: {
	label: string
	children: React.ReactNode
}) {
	if (children === undefined || children === null || children === '')
		return null
	return (
		<div>
			<dt className="text-xs text-gray-500">{label}</dt>
			<dd className="text-sm text-gray-900">{children}</dd>
		</div>
	)
}

function fieldText(value: unknown) {
	if (value === true) return 'Yes'
	if (value === false) return 'No'
	return value === undefined ? undefined : String(value)
}

const ADMIN_STATUSES: DeviceStatus[] = [
	'AVAILABLE',
	'IN_REPAIR',
	'LOST',
	'RETIRED',
]

export default function DevicePage() {
	const { device, events, user, locations, users, link, qr, loanDays } =
		useLoaderData<typeof loader>()
	const result = useActionData<typeof action>()
	const busy = useNavigation().state !== 'idle'
	const isAdmin = user.role === 'ADMIN'
	const isMine = device.holder?.userID === user.userID
	const defaultDue = new Date()
	defaultDue.setDate(defaultDue.getDate() + loanDays)
	const activeLocations = locations.filter((l) => !l.isArchived)

	return (
		<main className="mx-auto max-w-6xl space-y-4 p-4">
			<Link to="/devices" className="text-sm text-brand-600 hover:underline">
				← Devices
			</Link>
			<div className="flex flex-wrap items-start justify-between gap-3">
				<div>
					<h1 className="text-2xl font-semibold text-gray-900">
						<span className="font-mono">{device.assetTag}</span> · {device.name}
					</h1>
					<div className="mt-1 flex flex-wrap items-center gap-2 text-sm text-gray-600">
						<StatusBadge device={device} />
						<span>{device.type.name}</span>
						{device.holder && (
							<span>
								{device.status === 'ON_LOAN' ? 'On loan to' : 'Assigned to'}{' '}
								<strong>{device.holder.displayName}</strong>
								{device.dueAt && ` until ${formatDate(device.dueAt)}`}
							</span>
						)}
					</div>
				</div>
				{isAdmin && (
					<Link to={`/devices/${device.id}/edit`} className="btn-secondary">
						Edit
					</Link>
				)}
			</div>

			{result && 'error' in result && (
				<p
					role="alert"
					className="rounded-md bg-red-50 p-3 text-sm text-red-700"
				>
					{result.error}
				</p>
			)}
			{result && 'done' in result && (
				<p
					role="status"
					className="rounded-md bg-green-50 p-3 text-sm text-green-700"
				>
					Saved.
				</p>
			)}

			<div className="grid gap-4 lg:grid-cols-3">
				<div className="space-y-4 lg:col-span-2">
					{/* what the signed-in person can do with the device right now */}
					{device.type.usage === 'POOL' && device.status === 'AVAILABLE' && (
						<Form method="post" className="card space-y-3">
							<h2 className="font-semibold text-gray-900">
								Borrow this device
							</h2>
							<div className="grid gap-3 sm:grid-cols-2">
								<label className="text-sm text-gray-700">
									Return by
									<input
										type="date"
										name="dueAt"
										defaultValue={dateInputValue(defaultDue)}
										min={dateInputValue(new Date())}
										className="input mt-1"
									/>
								</label>
								<label className="text-sm text-gray-700">
									Note
									<input
										name="note"
										placeholder="Optional"
										className="input mt-1"
									/>
								</label>
							</div>
							<button
								type="submit"
								name="intent"
								value="check-out"
								disabled={busy}
								className="btn-primary"
							>
								Check out
							</button>
						</Form>
					)}
					{device.status === 'ON_LOAN' && (isMine || isAdmin) && (
						<Form method="post" className="card space-y-3">
							<h2 className="font-semibold text-gray-900">
								{isMine
									? 'Return this device'
									: `Return for ${device.holder?.displayName}`}
							</h2>
							<div className="grid gap-3 sm:grid-cols-2">
								<label className="text-sm text-gray-700">
									Put back in
									<select
										name="locationId"
										defaultValue={device.location?.id ?? ''}
										className="input mt-1"
									>
										<option value="">—</option>
										{activeLocations.map((l) => (
											<option key={l.id} value={l.id}>
												{l.name}
											</option>
										))}
									</select>
								</label>
								<label className="text-sm text-gray-700">
									Note
									<input
										name="note"
										placeholder="e.g. battery low, strap worn"
										className="input mt-1"
									/>
								</label>
							</div>
							<button
								type="submit"
								name="intent"
								value="check-in"
								disabled={busy}
								className="btn-primary"
							>
								Check in
							</button>
						</Form>
					)}
					{isAdmin &&
						device.type.usage === 'ASSIGNED' &&
						(device.status === 'AVAILABLE' || device.status === 'ASSIGNED') && (
							<Form method="post" className="card space-y-3">
								<h2 className="font-semibold text-gray-900">
									{device.holder ? 'Reassign' : 'Assign to a person'}
								</h2>
								<div className="grid gap-3 sm:grid-cols-2">
									<label className="text-sm text-gray-700">
										Person
										<select
											name="userId"
											required
											className="input mt-1"
											defaultValue=""
										>
											<option value="" disabled>
												Choose…
											</option>
											{users
												.filter((u) => u.userID !== device.holder?.userID)
												.map((u) => (
													<option key={u.userID} value={u.userID}>
														{u.displayName} ({u.username})
													</option>
												))}
										</select>
									</label>
									<label className="text-sm text-gray-700">
										Note
										<input
											name="note"
											placeholder="Optional"
											className="input mt-1"
										/>
									</label>
								</div>
								<div className="flex flex-wrap gap-2">
									<button
										type="submit"
										name="intent"
										value="assign"
										disabled={busy}
										className="btn-primary"
									>
										Assign
									</button>
									{device.status === 'ASSIGNED' && (
										<button
											type="submit"
											name="intent"
											value="unassign"
											disabled={busy}
											formNoValidate
											className="btn-secondary"
										>
											Take back
										</button>
									)}
								</div>
							</Form>
						)}

					<section className="card">
						<h2 className="mb-3 font-semibold text-gray-900">Details</h2>
						<dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
							<Detail label="Manufacturer">{device.manufacturer}</Detail>
							<Detail label="Model">{device.model}</Detail>
							<Detail label="Serial number">{device.serialNumber}</Detail>
							<Detail label="Location">{device.location?.name}</Detail>
							<Detail label="Purchased">
								{formatDate(device.purchaseDate)}
							</Detail>
							<Detail label="Warranty ends">
								{formatDate(device.warrantyEnd)}
							</Detail>
							{device.type.fields.map((field) => (
								<Detail key={field.key} label={field.label}>
									{fieldText(device.fields[field.key])}
								</Detail>
							))}
						</dl>
						{device.notes && (
							<p className="mt-3 whitespace-pre-wrap text-sm text-gray-700">
								{device.notes}
							</p>
						)}
					</section>

					<section className="card">
						<h2 className="mb-3 font-semibold text-gray-900">History</h2>
						<ol className="space-y-3">
							{events.map((event) => (
								<li
									key={event.id}
									className="border-l-2 border-gray-200 pl-3 text-sm"
								>
									<div className="text-gray-900">
										<strong>{EVENT_LABEL[event.kind]}</strong>
										{event.toStatus &&
											event.kind === 'STATUS_CHANGED' &&
											` → ${STATUS_LABEL[event.toStatus]}`}
										{event.holder && ` · ${event.holder.displayName}`}
										{event.details?.overdue === true && (
											<span className="ml-1 rounded-full bg-red-100 px-2 text-xs text-red-800">
												late
											</span>
										)}
									</div>
									<div className="text-xs text-gray-500">
										{formatDateTime(event.createdAt)}
										{event.actor && ` by ${event.actor.displayName}`}
										{Array.isArray(event.details?.changed) &&
											` · ${(event.details?.changed as string[]).join(', ')}`}
									</div>
									{event.note && (
										<div className="mt-1 text-gray-700">“{event.note}”</div>
									)}
								</li>
							))}
						</ol>
					</section>
				</div>

				<aside className="space-y-4" aria-label="Label and status">
					<section className="card text-center">
						<img
							src={qr}
							alt={`QR code for ${device.assetTag}`}
							className="mx-auto h-40 w-40"
						/>
						<p className="mt-2 break-all text-xs text-gray-500">{link}</p>
						<Link
							to={`/labels?ids=${device.id}`}
							className="btn-secondary mt-3"
						>
							Print label
						</Link>
					</section>
					{isAdmin && (
						<Form method="post" className="card space-y-3">
							<h2 className="font-semibold text-gray-900">Change status</h2>
							<select name="status" defaultValue="" required className="input">
								<option value="" disabled>
									Choose…
								</option>
								{ADMIN_STATUSES.filter((s) => s !== device.status).map((s) => (
									<option key={s} value={s}>
										{STATUS_LABEL[s]}
									</option>
								))}
							</select>
							<input
								name="note"
								placeholder="Reason (optional)"
								className="input"
							/>
							{device.holder && (
								<p className="text-xs text-gray-500">
									{device.holder.displayName} will no longer have it.
								</p>
							)}
							<button
								type="submit"
								name="intent"
								value="status"
								disabled={busy}
								className="btn-secondary w-full"
							>
								Change status
							</button>
						</Form>
					)}
				</aside>
			</div>
		</main>
	)
}
