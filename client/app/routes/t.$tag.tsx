import { type LoaderFunctionArgs, redirect } from '@remix-run/node'
import type { DeviceDetail } from '~/models/types'
import { APIError, requireUser } from '~/utility/API.server'

/** The address printed in QR labels: /t/VR-0001 opens the device (after signing in if needed). */
export async function loader({ request, params }: LoaderFunctionArgs) {
	const { api } = await requireUser(request)
	try {
		const detail = await api.get<DeviceDetail>(
			`/devices/by-tag/${encodeURIComponent(params.tag ?? '')}`
		)
		return redirect(`/devices/${detail.device.id}`)
	} catch (error) {
		if (error instanceof APIError && error.status === 404) {
			throw new Response('Not found', { status: 404 })
		}
		throw error
	}
}
