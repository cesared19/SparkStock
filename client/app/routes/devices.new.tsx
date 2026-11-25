import {
	type ActionFunctionArgs,
	type LoaderFunctionArgs,
	redirect,
} from '@remix-run/node'
import { useActionData, useLoaderData } from '@remix-run/react'
import DeviceForm, { readDeviceForm } from '~/components/DeviceForm'
import type { DeviceDetail, DeviceType, Location } from '~/models/types'
import { useT } from '~/i18n'
import { attempt, requireAdmin } from '~/utility/API.server'

export async function loader({ request }: LoaderFunctionArgs) {
	const { api } = await requireAdmin(request)
	const [types, locations] = await Promise.all([
		api.get<DeviceType[]>('/device-types'),
		api.get<Location[]>('/locations'),
	])
	return { types, locations }
}

export async function action({ request }: ActionFunctionArgs) {
	const { api } = await requireAdmin(request)
	const body = readDeviceForm(await request.formData())
	const result = await attempt(() => api.post<DeviceDetail>('/devices', body))
	if ('error' in result) return result
	return redirect(`/devices/${result.device.id}`)
}

export default function NewDevice() {
	const t = useT()
	const { types, locations } = useLoaderData<typeof loader>()
	const result = useActionData<typeof action>()
	return (
		<main className="mx-auto max-w-3xl space-y-4 p-4">
			<h1 className="text-2xl font-semibold text-gray-900">
				{t.form.addTitle}
			</h1>
			<DeviceForm types={types} locations={locations} error={result?.error} />
		</main>
	)
}
