import {
	type ActionFunctionArgs,
	type LoaderFunctionArgs,
} from '@remix-run/node'
import { Form, Link, useActionData, useNavigation } from '@remix-run/react'
import { useState } from 'react'
import { useT } from '~/i18n'
import { attempt, requireAdmin } from '~/utility/API.server'
import { parseCSV } from '~/utility/CSV'

type Target =
	| 'externalId'
	| 'name'
	| 'model'
	| 'manufacturer'
	| 'serialNumber'
	| 'typeName'
	| 'version'
	| 'accessories'
	| 'projectCode'
	| 'location'
	| 'notes'

// what each SparkStock field is called in a spreadsheet (English or Spanish headers), e.g. the Confluence
// "Testing Devices" database: ID, Device Name, Model, Brand, Serial Number, Type, Firmware, Attachments, Project Tag
const TARGETS: { key: Target; headers: string[] }[] = [
	{ key: 'externalId', headers: ['id', 'asset id', 'external id'] },
	{
		key: 'name',
		headers: ['device name', 'name', 'nombre', 'device', 'dispositivo'],
	},
	{ key: 'model', headers: ['model', 'modelo'] },
	{
		key: 'manufacturer',
		headers: ['brand', 'manufacturer', 'marca', 'fabricante', 'vendor'],
	},
	{
		key: 'serialNumber',
		headers: [
			'serial number',
			'serial',
			'sn',
			's/n',
			'número de serie',
			'numero de serie',
		],
	},
	{
		key: 'typeName',
		headers: ['type', 'tipo', 'category', 'categoría', 'categoria'],
	},
	{
		key: 'version',
		headers: ['firmware', 'version', 'os', 'system version', 'versión'],
	},
	{ key: 'accessories', headers: ['attachments', 'accessories', 'accesorios'] },
	{ key: 'projectCode', headers: ['project tag', 'project', 'proyecto'] },
	{ key: 'location', headers: ['location', 'ubicación', 'ubicacion'] },
	{ key: 'notes', headers: ['notes', 'notas', 'comments', 'comentarios'] },
]

interface RowResult {
	line: number
	action: 'CREATE' | 'UPDATE' | 'UNCHANGED' | 'ERROR'
	assetTag?: string
	name?: string
	typeName?: string
	message?: string
}

interface ImportResult {
	dryRun: boolean
	applied: boolean
	rows: RowResult[]
	newTypes: string[]
	newLocations: string[]
	created: number
	updated: number
	unchanged: number
	errors: number
}

export async function loader({ request }: LoaderFunctionArgs) {
	await requireAdmin(request)
	return null
}

export async function action({ request }: ActionFunctionArgs) {
	const { api } = await requireAdmin(request)
	const form = await request.formData()
	const rows = JSON.parse(String(form.get('rows') ?? '[]'))
	const dryRun = form.get('intent') !== 'import'
	return attempt(() =>
		api.post<ImportResult>('/import/devices', { rows, dryRun })
	)
}

function guess(headers: string[]) {
	const mapping: Partial<Record<Target, number>> = {}
	for (const target of TARGETS) {
		const index = headers.findIndex((h) =>
			target.headers.includes(h.trim().toLowerCase())
		)
		if (index >= 0) mapping[target.key] = index
	}
	return mapping
}

const ACTION_STYLE: Record<RowResult['action'], string> = {
	CREATE: 'bg-green-100 text-green-800',
	UPDATE: 'bg-blue-100 text-blue-800',
	UNCHANGED: 'bg-gray-100 text-gray-600',
	ERROR: 'bg-red-100 text-red-800',
}

export default function ImportDevices() {
	const t = useT()
	const result = useActionData<typeof action>()
	const busy = useNavigation().state !== 'idle'
	const [fileName, setFileName] = useState('')
	const [table, setTable] = useState<string[][]>([])
	const [mapping, setMapping] = useState<Partial<Record<Target, number>>>({})
	const headers = table[0] ?? []
	const body = table.slice(1)

	async function readFile(file: File | undefined) {
		if (!file) return
		const parsed = parseCSV(await file.text())
		setFileName(file.name)
		setTable(parsed)
		setMapping(guess(parsed[0] ?? []))
	}

	const rows = body.map((cells, index) => {
		const row: Record<string, string | number> = { line: index + 2 }
		for (const target of TARGETS) {
			const column = mapping[target.key]
			if (column !== undefined) row[target.key] = cells[column] ?? ''
		}
		return row
	})
	const report = result && 'rows' in result ? result : undefined

	return (
		<main className="mx-auto max-w-6xl space-y-4 p-4">
			<h1 className="text-2xl font-semibold text-gray-900">
				{t.importPage.title}
			</h1>
			<p className="text-sm text-gray-600">{t.importPage.intro}</p>

			<section className="card">
				<label className="text-sm font-medium text-gray-700">
					{t.importPage.file}
					<input
						type="file"
						accept=".csv,text/csv"
						onChange={(e) => readFile(e.target.files?.[0])}
						className="mt-1 block w-full text-sm text-gray-700"
					/>
				</label>
				{fileName && (
					<p className="mt-2 text-sm text-gray-600">
						{t.importPage.fileSummary(fileName, body.length, headers.length)}
					</p>
				)}
			</section>

			{headers.length > 0 && (
				<section className="card space-y-3">
					<h2 className="font-semibold text-gray-900">
						{t.importPage.columns}
					</h2>
					<div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
						{TARGETS.map((target) => {
							const [label, help] = t.importPage.targets[target.key]
							return (
								<label key={target.key} className="text-sm text-gray-700">
									{label}
									<select
										value={mapping[target.key] ?? ''}
										onChange={(e) =>
											setMapping({
												...mapping,
												[target.key]:
													e.target.value === ''
														? undefined
														: Number(e.target.value),
											})
										}
										className="input mt-1"
									>
										<option value="">{t.importPage.notImported}</option>
										{headers.map((header, index) => (
											<option key={index} value={index}>
												{header}
												{body[0]?.[index]
													? t.importPage.example(body[0][index].slice(0, 30))
													: ''}
											</option>
										))}
									</select>
									{help && (
										<span className="mt-1 block text-xs text-gray-500">
											{help}
										</span>
									)}
								</label>
							)
						})}
					</div>
					<Form method="post" className="flex flex-wrap gap-2">
						<input type="hidden" name="rows" value={JSON.stringify(rows)} />
						<button
							type="submit"
							name="intent"
							value="preview"
							disabled={busy || rows.length === 0}
							className="btn-secondary"
						>
							{t.importPage.preview}
						</button>
						{report && report.dryRun && report.errors === 0 && (
							<button
								type="submit"
								name="intent"
								value="import"
								disabled={busy}
								className="btn-primary"
							>
								{t.importPage.importN(report.created + report.updated)}
							</button>
						)}
					</Form>
				</section>
			)}

			{result && 'error' in result && (
				<p
					role="alert"
					className="rounded-md bg-red-50 p-3 text-sm text-red-700"
				>
					{result.error}
				</p>
			)}

			{report && (
				<section className="card space-y-3" aria-label={t.importPage.result}>
					<p
						role="status"
						className={`rounded-md p-3 text-sm ${
							report.errors > 0
								? 'bg-red-50 text-red-700'
								: report.applied
								? 'bg-green-50 text-green-700'
								: 'bg-blue-100 text-blue-800'
						}`}
					>
						{report.errors > 0
							? t.importPage.errors(report.errors)
							: report.applied
							? t.importPage.imported(
									report.created,
									report.updated,
									report.unchanged
							  )
							: t.importPage.previewed(
									report.created,
									report.updated,
									report.unchanged
							  )}
					</p>
					{report.newTypes.length > 0 && (
						<p className="text-sm text-gray-700">
							{t.importPage.newTypes}{' '}
							<strong>{report.newTypes.join(', ')}</strong>.{' '}
							{t.importPage.changeLater}{' '}
							<Link
								to="/admin/types"
								className="text-brand-600 hover:underline"
							>
								{t.nav.types}
							</Link>
							.
						</p>
					)}
					{report.newLocations.length > 0 && (
						<p className="text-sm text-gray-700">
							{t.importPage.newLocations}{' '}
							<strong>{report.newLocations.join(', ')}</strong>.
						</p>
					)}
					<table className="w-full text-left text-sm">
						<thead className="border-b border-gray-200 text-xs uppercase text-gray-500">
							<tr>
								<th className="py-2 pr-3 font-medium">{t.importPage.row}</th>
								<th className="py-2 pr-3 font-medium">
									{t.importPage.outcome}
								</th>
								<th className="py-2 pr-3 font-medium">{t.importPage.device}</th>
								<th className="hidden py-2 font-medium sm:table-cell">
									{t.importPage.details}
								</th>
							</tr>
						</thead>
						<tbody className="divide-y divide-gray-100">
							{report.rows.map((row) => (
								<tr key={row.line}>
									<td className="py-2 pr-3 text-gray-600">{row.line}</td>
									<td className="py-2 pr-3">
										<span
											className={`rounded-full px-2 py-0.5 text-xs font-medium ${
												ACTION_STYLE[row.action]
											}`}
										>
											{t.importPage.actions[row.action]}
										</span>
									</td>
									<td className="py-2 pr-3 text-gray-900">
										{row.assetTag && (
											<span className="mr-1 font-mono">{row.assetTag}</span>
										)}
										{row.name}{' '}
										<span className="text-xs text-gray-500">
											{row.typeName}
										</span>
										<div className="text-xs text-gray-600 sm:hidden">
											{row.message}
										</div>
									</td>
									<td className="hidden py-2 text-gray-600 sm:table-cell">
										{row.message}
									</td>
								</tr>
							))}
						</tbody>
					</table>
				</section>
			)}
		</main>
	)
}
