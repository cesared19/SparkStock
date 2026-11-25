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
import { useEffect, useState } from 'react'
import type {
	DeviceType,
	DeviceUsage,
	FieldDefinition,
	FieldKind,
} from '~/models/types'
import { attempt, requireAdmin } from '~/utility/API.server'

export async function loader({ request }: LoaderFunctionArgs) {
	const { api } = await requireAdmin(request)
	return { types: await api.get<DeviceType[]>('/device-types') }
}

export async function action({ request }: ActionFunctionArgs) {
	const { api } = await requireAdmin(request)
	const form = await request.formData()
	const id = String(form.get('id') ?? '')
	const body = JSON.parse(String(form.get('payload') ?? '{}'))
	return attempt(async () => {
		const type = id
			? await api.put<DeviceType>(`/device-types/${id}`, body)
			: await api.post<DeviceType>('/device-types', body)
		return { saved: type }
	})
}

const KINDS: FieldKind[] = ['TEXT', 'NUMBER', 'DATE', 'BOOLEAN', 'SELECT']

interface Draft {
	id?: number
	name: string
	tagPrefix: string
	usage: DeviceUsage
	coverageField: string
	isArchived: boolean
	fields: (FieldDefinition & { optionsText: string })[]
}

function toDraft(type?: DeviceType): Draft {
	return {
		id: type?.id,
		name: type?.name ?? '',
		tagPrefix: type?.tagPrefix ?? '',
		usage: type?.usage ?? 'POOL',
		coverageField: type?.coverageField ?? '',
		isArchived: type?.isArchived ?? false,
		fields: (type?.fields ?? []).map((f) => ({
			...f,
			optionsText: (f.options ?? []).join(', '),
		})),
	}
}

function keyFrom(label: string) {
	return label
		.toLowerCase()
		.normalize('NFD')
		.replace(/[̀-ͯ]/g, '')
		.replace(/[^a-z0-9]+/g, '_')
		.replace(/^_+|_+$/g, '')
		.replace(/^(\d)/, 'f_$1')
		.slice(0, 40)
}

export default function DeviceTypes() {
	const { types } = useLoaderData<typeof loader>()
	const result = useActionData<typeof action>()
	const busy = useNavigation().state !== 'idle'
	const [draft, setDraft] = useState<Draft | null>(null)

	useEffect(() => {
		if (result && 'saved' in result) setDraft(toDraft(result.saved))
	}, [result])

	const update = (patch: Partial<Draft>) =>
		draft && setDraft({ ...draft, ...patch })
	const updateField = (
		index: number,
		patch: Partial<Draft['fields'][number]>,
	) => {
		if (!draft) return
		const fields = draft.fields.map((f, i) =>
			i === index ? { ...f, ...patch } : f,
		)
		setDraft({ ...draft, fields })
	}
	const payload = draft && {
		name: draft.name,
		tagPrefix: draft.tagPrefix,
		usage: draft.usage,
		coverageField: draft.coverageField || null,
		isArchived: draft.isArchived,
		fields: draft.fields.map(({ optionsText, ...f }) => ({
			...f,
			options:
				f.kind === 'SELECT'
					? optionsText
							.split(',')
							.map((o) => o.trim())
							.filter(Boolean)
					: undefined,
		})),
	}

	return (
		<main className="mx-auto max-w-6xl space-y-4 p-4">
			<div className="flex items-center justify-between">
				<h1 className="text-2xl font-semibold text-gray-900">Device types</h1>
				<button
					type="button"
					className="btn-primary"
					onClick={() => setDraft(toDraft())}
				>
					New type
				</button>
			</div>
			<div className="grid gap-4 lg:grid-cols-3">
				<ul className="card divide-y divide-gray-100 self-start">
					{types.map((t) => (
						<li key={t.id}>
							<button
								type="button"
								onClick={() => setDraft(toDraft(t))}
								className={`w-full py-2 text-left text-sm ${draft?.id === t.id ? 'font-semibold text-brand-700' : 'text-gray-800'}`}
							>
								{t.name}{' '}
								<span className="font-mono text-xs text-gray-500">
									{t.tagPrefix}
								</span>
								<span className="block text-xs text-gray-500">
									{t.usage === 'POOL'
										? 'Lent to testers'
										: 'Assigned by admins'}{' '}
									· {t.fields.length} fields
									{t.isArchived && ' · archived'}
								</span>
							</button>
						</li>
					))}
				</ul>

				{draft && (
					<Form
						method="post"
						className="card space-y-4 lg:col-span-2"
						key={draft.id ?? 'new'}
					>
						<input type="hidden" name="id" value={draft.id ?? ''} />
						<input
							type="hidden"
							name="payload"
							value={JSON.stringify(payload)}
						/>
						<div className="grid gap-3 sm:grid-cols-3">
							<label className="text-sm text-gray-700">
								Name
								<input
									value={draft.name}
									onChange={(e) => update({ name: e.target.value })}
									required
									className="input mt-1"
								/>
							</label>
							<label className="text-sm text-gray-700">
								Tag prefix
								<input
									value={draft.tagPrefix}
									onChange={(e) =>
										update({ tagPrefix: e.target.value.toUpperCase() })
									}
									required
									maxLength={8}
									className="input mt-1 font-mono"
								/>
							</label>
							<label className="text-sm text-gray-700">
								How it&apos;s used
								<select
									value={draft.usage}
									onChange={(e) =>
										update({ usage: e.target.value as DeviceUsage })
									}
									className="input mt-1"
								>
									<option value="POOL">Lent (testers check out)</option>
									<option value="ASSIGNED">
										Assigned (admins give it to a person)
									</option>
								</select>
							</label>
						</div>

						<fieldset className="space-y-2">
							<legend className="text-sm font-semibold text-gray-900">
								Extra fields
							</legend>
							{draft.fields.map((field, index) => (
								<div
									key={index}
									className="grid gap-2 rounded-md border border-gray-200 p-2 sm:grid-cols-12"
								>
									<input
										aria-label="Label"
										placeholder="Label"
										value={field.label}
										onChange={(e) =>
											updateField(index, {
												label: e.target.value,
												// new fields take their key from the label until saved
												...(types
													.find((t) => t.id === draft.id)
													?.fields.some((f) => f.key === field.key)
													? {}
													: { key: keyFrom(e.target.value) }),
											})
										}
										className="input sm:col-span-4"
									/>
									<select
										aria-label="Kind"
										value={field.kind}
										onChange={(e) =>
											updateField(index, { kind: e.target.value as FieldKind })
										}
										className="input sm:col-span-2"
									>
										{KINDS.map((k) => (
											<option key={k}>{k}</option>
										))}
									</select>
									<input
										aria-label="Options"
										placeholder={
											field.kind === 'SELECT' ? 'Options, comma separated' : '—'
										}
										disabled={field.kind !== 'SELECT'}
										value={field.optionsText}
										onChange={(e) =>
											updateField(index, { optionsText: e.target.value })
										}
										className="input sm:col-span-3"
									/>
									<label className="flex items-center gap-1 text-xs text-gray-700 sm:col-span-2">
										<input
											type="checkbox"
											checked={field.required}
											onChange={(e) =>
												updateField(index, { required: e.target.checked })
											}
										/>
										Required
									</label>
									<button
										type="button"
										aria-label={`Remove ${field.label || 'field'}`}
										onClick={() =>
											setDraft({
												...draft,
												fields: draft.fields.filter((_, i) => i !== index),
											})
										}
										className="text-sm text-red-600 hover:text-red-700 sm:col-span-1"
									>
										Remove
									</button>
									<span className="font-mono text-xs text-gray-500 sm:col-span-12">
										key: {field.key || '—'}
									</span>
								</div>
							))}
							<button
								type="button"
								className="btn-secondary"
								onClick={() =>
									setDraft({
										...draft,
										fields: [
											...draft.fields,
											{
												key: '',
												label: '',
												kind: 'TEXT',
												required: false,
												optionsText: '',
											},
										],
									})
								}
							>
								Add field
							</button>
						</fieldset>

						<div className="grid gap-3 sm:grid-cols-2">
							<label className="text-sm text-gray-700">
								Version field for test coverage
								<select
									value={draft.coverageField}
									onChange={(e) => update({ coverageField: e.target.value })}
									className="input mt-1"
								>
									<option value="">None</option>
									{draft.fields
										.filter((f) => f.key)
										.map((f) => (
											<option key={f.key} value={f.key}>
												{f.label}
											</option>
										))}
								</select>
							</label>
							{draft.id && (
								<label className="flex items-center gap-2 self-end text-sm text-gray-700">
									<input
										type="checkbox"
										checked={draft.isArchived}
										onChange={(e) => update({ isArchived: e.target.checked })}
									/>
									Archived (no new devices of this type)
								</label>
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
						{result && 'saved' in result && result.saved.id === draft.id && (
							<p
								role="status"
								className="rounded-md bg-green-50 p-3 text-sm text-green-700"
							>
								Saved.
							</p>
						)}
						<button type="submit" disabled={busy} className="btn-primary">
							{draft.id ? 'Save type' : 'Create type'}
						</button>
					</Form>
				)}
			</div>
		</main>
	)
}
