import type { Device } from '~/models/types'
import { STATUS_LABEL, STATUS_STYLE } from '~/utility/Format'

export default function StatusBadge({
	device,
}: {
	device: Pick<Device, 'status' | 'isOverdue'>
}) {
	return (
		<span className="inline-flex flex-wrap gap-1">
			<span
				className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLE[device.status]}`}
			>
				{STATUS_LABEL[device.status]}
			</span>
			{device.isOverdue && (
				<span className="rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-800">
					Overdue
				</span>
			)}
		</span>
	)
}
