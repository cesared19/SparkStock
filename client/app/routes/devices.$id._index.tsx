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
import { type Dictionary, useT } from '~/i18n'
import type { DeviceDetail, DeviceStatus, Location, User } from '~/models/types'
import { attempt, requireAdmin, requireUser } from '~/utility/API.server'
import { dateInputValue, formatDate, formatDateTime } from '~/utility/Format'
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
				.then(() => ({ done: intent }))
		)
	}

	const { api } = await requireAdmin(request)
	switch (intent) {
		case 'assign':
			return attempt(() =>
				api
					.post(`${path}/assign`, { userId: form.get('userId') || null, note })
					.then(() => ({ done: intent }))
			)
		case 'unassign':
			return attempt(() =>
				api
					.post(`${path}/unassign`, { locationId, note })
					.then(() => ({ done: intent }))
			)
		case 'status':
			return attempt(() =>
				api
					.post(`${path}/status`, { status: form.get('status'), note })
					.then(() => ({ done: intent }))
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

function fieldText(value: unknown, t: Dictionary) {
	if (value === true) return t.yes
	if (value === false) return t.no
	return value === undefined ? undefined : String(value)
}

const ADMIN_STATUSES: DeviceStatus[] = [
	'AVAILABLE',
	'IN_REPAIR',
	'LOST',
	'RETIRED',
]

export default function DevicePage() {
	const t = useT()
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
				← {t.device.back}
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
								{device.status === 'ON_LOAN'
									? t.device.onLoanTo
									: t.device.assignedTo}{' '}
								<strong>{device.holder.displayName}</strong>
								{device.dueAt &&
									t.device.until(formatDate(device.dueAt, t.locale))}
							</span>
						)}
					</div>
				</div>
				{isAdmin && (
					<Link to={`/devices/${device.id}/edit`} className="btn-secondary">
						{t.device.edit}
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
					{t.saved}
				</p>
			)}

			<div className="grid gap-4 lg:grid-cols-3">
				<div className="space-y-4 lg:col-span-2">
					{device.type.usage === 'POOL' && device.status === 'AVAILABLE' && (
						<Form method="post" className="card space-y-3">
							<h2 className="font-semibold text-gray-900">
								{t.device.borrowTitle}
							</h2>
							<div className="grid gap-3 sm:grid-cols-2">
								<label className="text-sm text-gray-700">
									{t.device.returnBy}
									<input
										type="date"
										name="dueAt"
										defaultValue={dateInputValue(defaultDue)}
										min={dateInputValue(new Date())}
										className="input mt-1"
									/>
								</label>
								<label className="text-sm text-gray-700">
									{t.device.note}
									<input
										name="note"
										placeholder={t.optional}
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
								{t.device.checkOut}
							</button>
						</Form>
					)}
					{device.status === 'ON_LOAN' && (isMine || isAdmin) && (
						<Form method="post" className="card space-y-3">
							<h2 className="font-semibold text-gray-900">
								{isMine
									? t.device.returnTitle
									: t.device.returnFor(device.holder?.displayName ?? '')}
							</h2>
							<div className="grid gap-3 sm:grid-cols-2">
								<label className="text-sm text-gray-700">
									{t.device.putBack}
									<select
										name="locationId"
										defaultValue={device.location?.id ?? ''}
										className="input mt-1"
									>
										<option value="">{t.none}</option>
										{activeLocations.map((l) => (
											<option key={l.id} value={l.id}>
												{l.name}
											</option>
										))}
									</select>
								</label>
								<label className="text-sm text-gray-700">
									{t.device.note}
									<input
										name="note"
										placeholder={t.device.returnNote}
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
								{t.device.checkIn}
							</button>
						</Form>
					)}
					{isAdmin &&
						device.type.usage === 'ASSIGNED' &&
						(device.status === 'AVAILABLE' || device.status === 'ASSIGNED') && (
							<Form method="post" className="card space-y-3">
								<h2 className="font-semibold text-gray-900">
									{device.holder ? t.device.reassign : t.device.assign}
								</h2>
								<div className="grid gap-3 sm:grid-cols-2">
									<label className="text-sm text-gray-700">
										{t.device.person}
										<select
											name="userId"
											required
											className="input mt-1"
											defaultValue=""
										>
											<option value="" disabled>
												{t.choose}
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
										{t.device.note}
										<input
											name="note"
											placeholder={t.optional}
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
										{t.device.assignButton}
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
											{t.device.takeBack}
										</button>
									)}
								</div>
							</Form>
						)}

					<section className="card">
						<h2 className="mb-3 font-semibold text-gray-900">
							{t.device.details}
						</h2>
						<dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
							<Detail label={t.device.manufacturer}>
								{device.manufacturer}
							</Detail>
							<Detail label={t.device.model}>{device.model}</Detail>
							<Detail label={t.device.serial}>{device.serialNumber}</Detail>
							<Detail label={t.device.location}>{device.location?.name}</Detail>
							<Detail label={t.device.project}>{device.projectCode}</Detail>
							<Detail label={t.device.externalId}>{device.externalId}</Detail>
							<Detail label={t.device.purchased}>
								{formatDate(device.purchaseDate, t.locale)}
							</Detail>
							<Detail label={t.device.warrantyEnds}>
								{formatDate(device.warrantyEnd, t.locale)}
							</Detail>
							{device.type.fields.map((field) => (
								<Detail key={field.key} label={field.label}>
									{fieldText(device.fields[field.key], t)}
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
						<h2 className="mb-3 font-semibold text-gray-900">
							{t.device.history}
						</h2>
						<ol className="space-y-3">
							{events.map((event) => (
								<li
									key={event.id}
									className="border-l-2 border-gray-200 pl-3 text-sm"
								>
									<div className="text-gray-900">
										<strong>{t.events[event.kind]}</strong>
										{event.toStatus &&
											event.kind === 'STATUS_CHANGED' &&
											` → ${t.status[event.toStatus]}`}
										{event.holder && ` · ${event.holder.displayName}`}
										{event.details?.overdue === true && (
											<span className="ml-1 rounded-full bg-red-100 px-2 text-xs text-red-800">
												{t.device.late}
											</span>
										)}
									</div>
									<div className="text-xs text-gray-500">
										{formatDateTime(event.createdAt, t.locale)}
										{event.actor && t.device.by(event.actor.displayName)}
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

				<aside className="space-y-4" aria-label={t.device.labelAndStatus}>
					<section className="card text-center">
						<img
							src={qr}
							alt={t.device.qrAlt(device.assetTag)}
							className="mx-auto h-40 w-40"
						/>
						<p className="mt-2 break-all text-xs text-gray-500">{link}</p>
						<Link
							to={`/labels?ids=${device.id}`}
							className="btn-secondary mt-3"
						>
							{t.device.printLabel}
						</Link>
					</section>
					{isAdmin && (
						<Form method="post" className="card space-y-3">
							<h2 className="font-semibold text-gray-900">
								{t.device.changeStatus}
							</h2>
							<select
								name="status"
								defaultValue=""
								required
								aria-label={t.device.changeStatus}
								className="input"
							>
								<option value="" disabled>
									{t.choose}
								</option>
								{ADMIN_STATUSES.filter((s) => s !== device.status).map((s) => (
									<option key={s} value={s}>
										{t.status[s]}
									</option>
								))}
							</select>
							<input
								name="note"
								placeholder={t.device.reason}
								aria-label={t.device.reason}
								className="input"
							/>
							{device.holder && (
								<p className="text-xs text-gray-500">
									{t.device.willLose(device.holder.displayName)}
								</p>
							)}
							<button
								type="submit"
								name="intent"
								value="status"
								disabled={busy}
								className="btn-secondary w-full"
							>
								{t.device.changeStatus}
							</button>
						</Form>
					)}
				</aside>
			</div>
		</main>
	)
}
