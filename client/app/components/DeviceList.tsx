import { Link } from '@remix-run/react'
import { type Dictionary, useT } from '~/i18n'
import type { Device } from '~/models/types'
import { formatDate } from '~/utility/Format'
import StatusBadge from './StatusBadge'

function holderText(device: Device, t: Dictionary) {
	if (!device.holder) return device.location?.name ?? ''
	return device.dueAt
		? `${device.holder.displayName} · ${t.list.until(
				formatDate(device.dueAt, t.locale)
		  )}`
		: device.holder.displayName
}

/** A table on wide screens, cards on phones. */
export default function DeviceList({
	devices,
	empty,
}: {
	devices: Device[]
	empty: string
}) {
	const t = useT()
	if (devices.length === 0) {
		return <p className="py-6 text-center text-sm text-gray-500">{empty}</p>
	}
	return (
		<>
			<table className="hidden w-full text-left text-sm md:table">
				<thead className="border-b border-gray-200 text-xs uppercase text-gray-500">
					<tr>
						<th className="py-2 pr-3 font-medium">{t.list.tag}</th>
						<th className="py-2 pr-3 font-medium">{t.list.device}</th>
						<th className="py-2 pr-3 font-medium">{t.list.version}</th>
						<th className="py-2 pr-3 font-medium">{t.list.status}</th>
						<th className="py-2 font-medium">{t.list.with}</th>
					</tr>
				</thead>
				<tbody className="divide-y divide-gray-100">
					{devices.map((device) => (
						<tr key={device.id} className="hover:bg-gray-50">
							<td className="py-2 pr-3 font-mono">
								<Link
									to={`/devices/${device.id}`}
									className="text-brand-600 hover:underline"
								>
									{device.assetTag}
								</Link>
							</td>
							<td className="py-2 pr-3">
								<div className="text-gray-900">{device.name}</div>
								<div className="text-xs text-gray-500">
									{device.type.name}
									{device.serialNumber && ` · SN ${device.serialNumber}`}
								</div>
							</td>
							<td className="py-2 pr-3 text-gray-700">
								{device.coverageVersion ?? ''}
							</td>
							<td className="py-2 pr-3">
								<StatusBadge device={device} />
							</td>
							<td className="py-2 text-gray-700">{holderText(device, t)}</td>
						</tr>
					))}
				</tbody>
			</table>
			<ul className="space-y-2 md:hidden">
				{devices.map((device) => (
					<li key={device.id}>
						<Link
							to={`/devices/${device.id}`}
							className="card block hover:bg-gray-50"
						>
							<div className="flex items-start justify-between gap-2">
								<div>
									<div className="font-mono text-sm text-brand-600">
										{device.assetTag}
									</div>
									<div className="text-sm text-gray-900">{device.name}</div>
								</div>
								<StatusBadge device={device} />
							</div>
							<div className="mt-1 text-xs text-gray-500">
								{[
									device.type.name,
									device.coverageVersion,
									holderText(device, t),
								]
									.filter(Boolean)
									.join(' · ')}
							</div>
						</Link>
					</li>
				))}
			</ul>
		</>
	)
}
