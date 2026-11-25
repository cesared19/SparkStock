import { type LoaderFunctionArgs } from '@remix-run/node'
import { Form, useLoaderData, useSearchParams } from '@remix-run/react'
import { useState } from 'react'
import StatusBadge from '~/components/StatusBadge'
import { useT } from '~/i18n'
import type { Device, DeviceType } from '~/models/types'
import { requireUser } from '~/utility/API.server'
import { buildLabelsPDF, SHEETS } from '~/utility/Labels'
import { publicURL } from '~/utility/PublicURL.server'

export async function loader({ request }: LoaderFunctionArgs) {
	const { api } = await requireUser(request)
	const url = new URL(request.url)
	const query = new URLSearchParams()
	for (const name of ['q', 'typeId']) {
		const value = url.searchParams.get(name)
		if (value) query.set(name, value)
	}
	const [devices, types] = await Promise.all([
		api.get<Device[]>(`/devices?${query}`),
		api.get<DeviceType[]>('/device-types'),
	])
	return { devices, types, base: publicURL(request) }
}

export default function Labels() {
	const t = useT()
	const { devices, types, base } = useLoaderData<typeof loader>()
	const [params] = useSearchParams()
	const preselected = new Set(
		(params.get('ids') ?? '').split(',').filter(Boolean)
	)
	const [selected, setSelected] = useState<Set<string>>(
		preselected.size > 0 ? preselected : new Set<string>()
	)
	const [sheetId, setSheetId] = useState(SHEETS[0].id)
	const [skip, setSkip] = useState(0)
	const [working, setWorking] = useState(false)
	const sheet = SHEETS.find((s) => s.id === sheetId) ?? SHEETS[0]
	const filtered = Boolean(params.get('q') || params.get('typeId'))
	const shown =
		preselected.size > 0 && !filtered
			? devices.filter((d) => preselected.has(d.id))
			: devices

	const toggle = (id: string) => {
		const next = new Set(selected)
		if (next.has(id)) next.delete(id)
		else next.add(id)
		setSelected(next)
	}
	const allShownSelected =
		shown.length > 0 && shown.every((d) => selected.has(d.id))

	async function download() {
		setWorking(true)
		try {
			const items = devices
				.filter((d) => selected.has(d.id))
				.map((d) => ({
					assetTag: d.assetTag,
					name: d.name,
					serialNumber: d.serialNumber,
					link: `${base}/t/${encodeURIComponent(d.assetTag)}`,
				}))
			const doc = await buildLabelsPDF(items, sheet, skip)
			doc.save(
				`${t.labels.fileName}-${new Date().toISOString().slice(0, 10)}.pdf`
			)
		} finally {
			setWorking(false)
		}
	}

	const perSheet = sheet.columns * sheet.rows
	return (
		<main className="mx-auto max-w-6xl space-y-4 p-4">
			<h1 className="text-2xl font-semibold text-gray-900">{t.labels.title}</h1>
			<p className="text-sm text-gray-600">{t.labels.intro}</p>
			<section className="card grid gap-3 sm:grid-cols-3">
				<label className="text-sm text-gray-700 sm:col-span-2">
					{t.labels.sheet}
					<select
						value={sheetId}
						onChange={(e) => setSheetId(e.target.value)}
						className="input mt-1"
					>
						{SHEETS.map((s) => (
							<option key={s.id} value={s.id}>
								{t.labels.sheets[s.id] ?? s.name}
							</option>
						))}
					</select>
				</label>
				<label className="text-sm text-gray-700">
					{t.labels.used}
					<input
						type="number"
						min={0}
						max={perSheet - 1}
						value={skip}
						onChange={(e) =>
							setSkip(
								Math.max(0, Math.min(perSheet - 1, Number(e.target.value) || 0))
							)
						}
						className="input mt-1"
					/>
				</label>
				<div className="flex flex-wrap items-center gap-3 sm:col-span-3">
					<button
						type="button"
						onClick={download}
						disabled={selected.size === 0 || working}
						className="btn-primary"
					>
						{working ? t.labels.preparing : t.labels.download(selected.size)}
					</button>
					{selected.size > 0 && (
						<button
							type="button"
							onClick={() => setSelected(new Set())}
							className="btn-secondary"
						>
							{t.labels.clear}
						</button>
					)}
				</div>
			</section>

			<Form method="get" className="card grid gap-3 sm:grid-cols-3">
				<input
					type="search"
					name="q"
					defaultValue={params.get('q') ?? ''}
					placeholder={t.labels.search}
					aria-label={t.labels.search}
					className="input sm:col-span-2"
				/>
				<div className="flex gap-2">
					<select
						name="typeId"
						defaultValue={params.get('typeId') ?? ''}
						aria-label={t.list.type}
						className="input"
					>
						<option value="">{t.list.allTypes}</option>
						{types.map((type) => (
							<option key={type.id} value={type.id}>
								{type.name}
							</option>
						))}
					</select>
					<button type="submit" className="btn-secondary">
						{t.labels.filter}
					</button>
				</div>
			</Form>

			<section className="card">
				<label className="mb-2 flex items-center gap-2 border-b border-gray-200 pb-2 text-sm font-medium text-gray-700">
					<input
						type="checkbox"
						checked={allShownSelected}
						onChange={() => {
							const next = new Set(selected)
							shown.forEach((d) =>
								allShownSelected ? next.delete(d.id) : next.add(d.id)
							)
							setSelected(next)
						}}
					/>
					{t.labels.selectAll(shown.length)}
				</label>
				<ul className="divide-y divide-gray-100">
					{shown.map((d) => (
						<li key={d.id}>
							<label className="flex items-center gap-3 py-2 text-sm">
								<input
									type="checkbox"
									checked={selected.has(d.id)}
									onChange={() => toggle(d.id)}
								/>
								<span className="font-mono text-gray-900">{d.assetTag}</span>
								<span className="flex-1 text-gray-700">{d.name}</span>
								<StatusBadge device={d} />
							</label>
						</li>
					))}
				</ul>
			</section>
		</main>
	)
}
