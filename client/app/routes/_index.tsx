import { type LoaderFunctionArgs } from '@remix-run/node'
import { Link, useLoaderData } from '@remix-run/react'
import DeviceList from '~/components/DeviceList'
import type { Dashboard, DeviceStatus } from '~/models/types'
import { requireUser } from '~/utility/API.server'
import { formatDate, STATUS_LABEL } from '~/utility/Format'

export async function loader({ request }: LoaderFunctionArgs) {
	const { api, user } = await requireUser(request)
	const dashboard = await api.get<Dashboard>('/dashboard')
	return { dashboard, user }
}

const SHOWN: DeviceStatus[] = [
	'AVAILABLE',
	'ON_LOAN',
	'ASSIGNED',
	'IN_REPAIR',
	'LOST',
]

export default function DashboardPage() {
	const { dashboard, user } = useLoaderData<typeof loader>()
	return (
		<main className="mx-auto max-w-6xl space-y-6 p-4">
			<h1 className="text-2xl font-semibold text-gray-900">
				Hi, {user.displayName}
			</h1>

			<section
				aria-label="Devices by status"
				className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5"
			>
				{SHOWN.map((status) => (
					<Link
						key={status}
						to={`/devices?status=${status}`}
						className="card hover:bg-gray-50"
					>
						<div className="text-2xl font-semibold text-gray-900">
							{dashboard.counts[status] ?? 0}
						</div>
						<div className="text-sm text-gray-600">{STATUS_LABEL[status]}</div>
					</Link>
				))}
			</section>

			<section className="card space-y-3">
				<div className="flex items-center justify-between">
					<h2 className="font-semibold text-gray-900">My devices</h2>
					<Link
						to="/devices?status=AVAILABLE"
						className="text-sm text-brand-600 hover:underline"
					>
						Borrow a device
					</Link>
				</div>
				<DeviceList
					devices={dashboard.myDevices}
					empty="You don't have any device."
				/>
			</section>

			<section className="card space-y-3">
				<h2 className="font-semibold text-gray-900">Overdue loans</h2>
				<DeviceList
					devices={dashboard.overdue}
					empty="Every loan is on time."
				/>
			</section>

			{dashboard.warrantyEnding.length > 0 && (
				<section className="card space-y-3">
					<h2 className="font-semibold text-gray-900">Warranty ending soon</h2>
					<ul className="divide-y divide-gray-100 text-sm">
						{dashboard.warrantyEnding.map((device) => (
							<li key={device.id} className="flex justify-between gap-2 py-2">
								<Link
									to={`/devices/${device.id}`}
									className="text-brand-600 hover:underline"
								>
									{device.assetTag} · {device.name}
								</Link>
								<span className="text-gray-600">
									{formatDate(device.warrantyEnd)}
								</span>
							</li>
						))}
					</ul>
				</section>
			)}
		</main>
	)
}
