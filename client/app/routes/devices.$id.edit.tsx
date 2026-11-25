import {
	type ActionFunctionArgs,
	type LoaderFunctionArgs,
	redirect,
} from '@remix-run/node'
import { Link, useActionData, useLoaderData } from '@remix-run/react'
import DeviceForm, { readDeviceForm } from '~/components/DeviceForm'
import type { DeviceDetail, DeviceType, Location } from '~/models/types'
import { useT } from '~/i18n'
import { attempt, requireAdmin } from '~/utility/API.server'

export async function loader({ request, params }: LoaderFunctionArgs) {
	const { api } = await requireAdmin(request)
	const [detail, types, locations] = await Promise.all([
		api.get<DeviceDetail>(`/devices/${params.id}`),
		api.get<DeviceType[]>('/device-types'),
		api.get<Location[]>('/locations'),
	])
	return { device: detail.device, types, locations }
}

export async function action({ request, params }: ActionFunctionArgs) {
	const { api } = await requireAdmin(request)
	const body = readDeviceForm(await request.formData())
	const result = await attempt(() =>
		api.put<DeviceDetail>(`/devices/${params.id}`, body)
	)
	if ('error' in result) return result
	return redirect(`/devices/${params.id}`)
}

export default function EditDevice() {
	const t = useT()
	const { device, types, locations } = useLoaderData<typeof loader>()
	const result = useActionData<typeof action>()
	return (
		<main className="mx-auto max-w-3xl space-y-4 p-4">
			<Link
				to={`/devices/${device.id}`}
				className="text-sm text-brand-600 hover:underline"
			>
				← {device.assetTag}
			</Link>
			<h1 className="text-2xl font-semibold text-gray-900">
				{t.form.editTitle(device.assetTag)}
			</h1>
			<DeviceForm
				types={types}
				locations={locations}
				device={device}
				error={result?.error}
			/>
		</main>
	)
}
