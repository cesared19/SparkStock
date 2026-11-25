import {
	type ActionFunctionArgs,
	type LoaderFunctionArgs,
} from '@remix-run/node'
import {
	Form,
	useActionData,
	useLoaderData,
	useNavigation,
} from '@remix-run/react'
import { useT } from '~/i18n'
import type { Location } from '~/models/types'
import { attempt, requireAdmin } from '~/utility/API.server'

export async function loader({ request }: LoaderFunctionArgs) {
	const { api } = await requireAdmin(request)
	return { locations: await api.get<Location[]>('/locations') }
}

export async function action({ request }: ActionFunctionArgs) {
	const { api } = await requireAdmin(request)
	const form = await request.formData()
	const id = String(form.get('id') ?? '')
	const name = String(form.get('name') ?? '')
	if (id) {
		const isArchived = form.get('isArchived') === 'true'
		return attempt(() =>
			api
				.put<Location>(`/locations/${id}`, { name, isArchived })
				.then(() => ({ ok: true }))
		)
	}
	return attempt(() =>
		api.post<Location>('/locations', { name }).then(() => ({ ok: true }))
	)
}

export default function Locations() {
	const t = useT()
	const { locations } = useLoaderData<typeof loader>()
	const result = useActionData<typeof action>()
	const busy = useNavigation().state !== 'idle'
	return (
		<main className="mx-auto max-w-3xl space-y-4 p-4">
			<h1 className="text-2xl font-semibold text-gray-900">
				{t.locations.title}
			</h1>
			<p className="text-sm text-gray-600">{t.locations.intro}</p>
			<Form method="post" className="card flex gap-2">
				<input
					name="name"
					required
					placeholder={t.locations.placeholder}
					aria-label={t.locations.newLabel}
					className="input"
				/>
				<button type="submit" disabled={busy} className="btn-primary shrink-0">
					{t.locations.add}
				</button>
			</Form>
			{result && 'error' in result && (
				<p
					role="alert"
					className="rounded-md bg-red-50 p-3 text-sm text-red-700"
				>
					{result.error}
				</p>
			)}
			<ul className="card divide-y divide-gray-100">
				{locations.map((location) => (
					<li key={location.id}>
						<Form
							method="post"
							className="flex flex-wrap items-center gap-2 py-2"
						>
							<input type="hidden" name="id" value={location.id} />
							<input
								name="name"
								defaultValue={location.name}
								aria-label={t.locations.nameOf(location.name)}
								className="input flex-1"
							/>
							<button
								type="submit"
								name="isArchived"
								value={String(location.isArchived)}
								className="btn-secondary"
							>
								{t.locations.rename}
							</button>
							<button
								type="submit"
								name="isArchived"
								value={String(!location.isArchived)}
								className="btn-secondary"
							>
								{location.isArchived
									? t.locations.restore
									: t.locations.archive}
							</button>
						</Form>
					</li>
				))}
			</ul>
		</main>
	)
}
