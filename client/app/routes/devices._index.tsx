import { type LoaderFunctionArgs } from '@remix-run/node'
import {
	Form,
	Link,
	useLoaderData,
	useSearchParams,
	useSubmit,
} from '@remix-run/react'
import DeviceList from '~/components/DeviceList'
import type { Device, DeviceStatus, DeviceType } from '~/models/types'
import { requireUser } from '~/utility/API.server'
import { STATUS_LABEL } from '~/utility/Format'

const FILTERS = ['q', 'typeId', 'status', 'holder', 'overdue', 'includeRetired']

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
	const { devices, types, user } = useLoaderData<typeof loader>()
	const [params] = useSearchParams()
	const submit = useSubmit()
	return (
		<main className="mx-auto max-w-6xl space-y-4 p-4">
			<div className="flex flex-wrap items-center justify-between gap-2">
				<h1 className="text-2xl font-semibold text-gray-900">Devices</h1>
				{user.role === 'ADMIN' && (
					<Link to="/devices/new" className="btn-primary">
						Add device
					</Link>
				)}
			</div>
			<Form
				method="get"
				className="card grid gap-3 sm:grid-cols-2 lg:grid-cols-5"
				onChange={(e) => submit(e.currentTarget, { replace: true })}
			>
				<label className="text-sm text-gray-700 lg:col-span-2">
					<span className="sr-only">Search</span>
					<input
						type="search"
						name="q"
						defaultValue={params.get('q') ?? ''}
						placeholder="Search tag, model, serial, person, version…"
						className="input"
					/>
				</label>
				<label className="text-sm text-gray-700">
					<span className="sr-only">Type</span>
					<select
						name="typeId"
						defaultValue={params.get('typeId') ?? ''}
						className="input"
					>
						<option value="">All types</option>
						{types.map((t) => (
							<option key={t.id} value={t.id}>
								{t.name}
							</option>
						))}
					</select>
				</label>
				<label className="text-sm text-gray-700">
					<span className="sr-only">Status</span>
					<select
						name="status"
						defaultValue={params.get('status') ?? ''}
						className="input"
					>
						<option value="">All statuses</option>
						{(Object.keys(STATUS_LABEL) as DeviceStatus[]).map((s) => (
							<option key={s} value={s}>
								{STATUS_LABEL[s]}
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
						Mine
					</label>
					<label className="flex items-center gap-1">
						<input
							type="checkbox"
							name="overdue"
							value="true"
							defaultChecked={params.get('overdue') === 'true'}
						/>{' '}
						Overdue
					</label>
					<label className="flex items-center gap-1">
						<input
							type="checkbox"
							name="includeRetired"
							value="true"
							defaultChecked={params.get('includeRetired') === 'true'}
						/>{' '}
						Retired
					</label>
				</div>
			</Form>
			<p className="text-sm text-gray-600" role="status">
				{devices.length} {devices.length === 1 ? 'device' : 'devices'}
			</p>
			<section className="card">
				<DeviceList
					devices={devices}
					empty="No device matches these filters."
				/>
			</section>
		</main>
	)
}
