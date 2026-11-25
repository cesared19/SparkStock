import { type LoaderFunctionArgs } from '@remix-run/node'
import {
	Form,
	Link,
	useLoaderData,
	useSearchParams,
	useSubmit,
} from '@remix-run/react'
import DeviceList from '~/components/DeviceList'
import { useT } from '~/i18n'
import type { Device, DeviceStatus, DeviceType } from '~/models/types'
import { requireUser } from '~/utility/API.server'

const FILTERS = ['q', 'typeId', 'status', 'holder', 'overdue', 'includeRetired']
const STATUSES: DeviceStatus[] = [
	'AVAILABLE',
	'ON_LOAN',
	'ASSIGNED',
	'IN_REPAIR',
	'LOST',
	'RETIRED',
]

export async function loader({ request }: LoaderFunctionArgs) {
	const { api, user } = await requireUser(request)
	const url = new URL(request.url)
	const query = new URLSearchParams()
	for (const name of FILTERS) {
		const value = url.searchParams.get(name)
		if (value) query.set(name, value)
	}
	const [devices, types] = await Promise.all([
		api.get<Device[]>(`/devices?${query}`),
		api.get<DeviceType[]>('/device-types'),
	])
	return { devices, types, user }
}

export default function Devices() {
	const t = useT()
	const { devices, types, user } = useLoaderData<typeof loader>()
	const [params] = useSearchParams()
	const submit = useSubmit()
	return (
		<main className="mx-auto max-w-6xl space-y-4 p-4">
			<div className="flex flex-wrap items-center justify-between gap-2">
				<h1 className="text-2xl font-semibold text-gray-900">{t.list.title}</h1>
				{user.role === 'ADMIN' && (
					<Link to="/devices/new" className="btn-primary">
						{t.list.add}
					</Link>
				)}
			</div>
			<Form
				method="get"
				className="card grid gap-3 sm:grid-cols-2 lg:grid-cols-5"
				onChange={(e) => submit(e.currentTarget, { replace: true })}
			>
				<label className="text-sm text-gray-700 lg:col-span-2">
					<span className="sr-only">{t.list.search}</span>
					<input
						type="search"
						name="q"
						defaultValue={params.get('q') ?? ''}
						placeholder={t.list.searchPlaceholder}
						className="input"
					/>
				</label>
				<label className="text-sm text-gray-700">
					<span className="sr-only">{t.list.type}</span>
					<select
						name="typeId"
						defaultValue={params.get('typeId') ?? ''}
						className="input"
					>
						<option value="">{t.list.allTypes}</option>
						{types.map((type) => (
							<option key={type.id} value={type.id}>
								{type.name}
							</option>
						))}
					</select>
				</label>
				<label className="text-sm text-gray-700">
					<span className="sr-only">{t.list.status}</span>
					<select
						name="status"
						defaultValue={params.get('status') ?? ''}
						className="input"
					>
						<option value="">{t.list.allStatuses}</option>
						{STATUSES.map((status) => (
							<option key={status} value={status}>
								{t.status[status]}
							</option>
						))}
					</select>
				</label>
				<div className="flex flex-wrap items-center gap-3 text-sm text-gray-700">
					<label className="flex items-center gap-1">
						<input
							type="checkbox"
							name="holder"
							value="me"
							defaultChecked={params.get('holder') === 'me'}
						/>{' '}
						{t.list.mine}
					</label>
					<label className="flex items-center gap-1">
						<input
							type="checkbox"
							name="overdue"
							value="true"
							defaultChecked={params.get('overdue') === 'true'}
						/>{' '}
						{t.list.overdue}
					</label>
					<label className="flex items-center gap-1">
						<input
							type="checkbox"
							name="includeRetired"
							value="true"
							defaultChecked={params.get('includeRetired') === 'true'}
						/>{' '}
						{t.list.retired}
					</label>
				</div>
			</Form>
			<p className="text-sm text-gray-600" role="status">
				{t.list.count(devices.length)}
			</p>
			<section className="card">
				<DeviceList devices={devices} empty={t.list.empty} />
			</section>
		</main>
	)
}
