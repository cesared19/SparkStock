import {
	type ActionFunctionArgs,
	type LoaderFunctionArgs,
} from '@remix-run/node'
import {
	Form,
	useActionData,
	useLoaderData,
	useNavigation,
	useSubmit,
} from '@remix-run/react'
import { useEffect, useRef } from 'react'
import type {
	CoverageMatrix,
	CoverageRow,
	CoverageState,
	KnownModel,
	SparkyTCProject,
} from '~/models/types'
import {
	APIError,
	attempt,
	requireAdmin,
	requireUser,
} from '~/utility/API.server'

export async function loader({ request }: LoaderFunctionArgs) {
	const { api, user } = await requireUser(request)
	const url = new URL(request.url)
	let projects: SparkyTCProject[] = []
	let knownModels: KnownModel[] = []
	try {
		const data = await api.get<{
			projects: SparkyTCProject[]
			knownModels: KnownModel[]
		}>('/coverage/projects')
		projects = data.projects
		knownModels = data.knownModels
	} catch (error) {
		if (error instanceof APIError)
			return {
				user,
				error: error.message,
				projects,
				knownModels,
				matrix: null,
				project: null,
			}
		throw error
	}
	const project =
		projects.find(
			(p) => p.projectShortCode === url.searchParams.get('project')
		) ??
		projects[0] ??
		null
	if (!project)
		return {
			user,
			error: undefined,
			projects,
			knownModels,
			matrix: null,
			project: null,
		}
	const build = url.searchParams.get('build') || project.builds[0] || ''
	const query = new URLSearchParams({
		project: project.projectShortCode,
		...(build ? { build } : {}),
	})
	const matrix = await api.get<CoverageMatrix>(`/coverage?${query}`)
	return { user, error: undefined, projects, knownModels, matrix, project }
}

export async function action({ request }: ActionFunctionArgs) {
	const { api } = await requireAdmin(request)
	const form = await request.formData()
	if (form.get('intent') === 'delete') {
		return attempt(() =>
			api
				.delete(`/coverage/targets/${form.get('id')}`)
				.then(() => ({ ok: true }))
		)
	}
	// the model can be picked from the stock as "model|version", or typed
	const picked = String(form.get('known') ?? '')
	const [knownModel, knownVersion] = picked ? picked.split('|') : ['', '']
	const model = String(form.get('model') ?? '').trim() || knownModel
	const version =
		form.get('anyVersion') === 'on'
			? null
			: String(form.get('version') ?? '').trim() || knownVersion || null
	return attempt(() =>
		api
			.post('/coverage/targets', {
				projectCode: form.get('projectCode'),
				model,
				version,
			})
			.then(() => ({ added: Date.now() }))
	)
}

const STATE: Record<CoverageState, { label: string; style: string }> = {
	PASSING: { label: 'Tested, passing', style: 'bg-green-100 text-green-800' },
	FAILING: {
		label: 'Tested, with failures',
		style: 'bg-yellow-100 text-yellow-800',
	},
	MISSING: { label: 'Not tested', style: 'bg-red-100 text-red-800' },
}

function Counts({ row }: { row: CoverageRow }) {
	if (row.state === 'MISSING') return <span className="text-gray-500">—</span>
	const parts = [
		row.passed && `${row.passed} passed`,
		row.failed && `${row.failed} failed`,
		row.blocked && `${row.blocked} blocked`,
		row.skipped && `${row.skipped} skipped`,
	].filter(Boolean)
	return <span>{parts.join(' · ')}</span>
}

export default function Coverage() {
	const { user, error, projects, knownModels, matrix, project } =
		useLoaderData<typeof loader>()
	const result = useActionData<typeof action>()
	const busy = useNavigation().state !== 'idle'
	const submit = useSubmit()
	const isAdmin = user.role === 'ADMIN'
	// the form starts empty again after each target added, so the next one doesn't inherit its choices
	const addForm = useRef<HTMLFormElement>(null)
	const added = result && 'added' in result ? result.added : undefined
	useEffect(() => {
		if (added) addForm.current?.reset()
	}, [added])
	const targetRows = matrix?.rows.filter((row) => row.target) ?? []
	const otherRows = matrix?.rows.filter((row) => !row.target) ?? []

	return (
		<main className="mx-auto max-w-6xl space-y-4 p-4">
			<div>
				<h1 className="text-2xl font-semibold text-gray-900">Test coverage</h1>
				<p className="mt-1 text-sm text-gray-600">
					Which device models and versions each SparkyTC project was tested on
					in a build, from the device recorded with each result.
				</p>
			</div>

			{error && (
				<p
					role="alert"
					className="rounded-md bg-red-50 p-3 text-sm text-red-700"
				>
					{error}
				</p>
			)}
			{!error && projects.length === 0 && (
				<p className="text-sm text-gray-600">SparkyTC has no projects yet.</p>
			)}

			{project && (
				<Form
					method="get"
					className="card grid gap-3 sm:grid-cols-2"
					onChange={(e) => submit(e.currentTarget, { replace: true })}
				>
					<label className="text-sm text-gray-700">
						Project
						<select
							name="project"
							defaultValue={project.projectShortCode}
							className="input mt-1"
						>
							{projects.map((p) => (
								<option key={p.projectShortCode} value={p.projectShortCode}>
									{p.title} ({p.projectShortCode})
								</option>
							))}
						</select>
					</label>
					<label className="text-sm text-gray-700">
						Build
						<select
							name="build"
							key={project.projectShortCode}
							defaultValue={matrix?.build ?? ''}
							disabled={project.builds.length === 0}
							className="input mt-1"
						>
							{project.builds.length === 0 && (
								<option value="">No builds yet</option>
							)}
							{project.builds.map((build, index) => (
								<option key={build} value={build}>
									{build}
									{index === 0 ? ' (current)' : ''}
								</option>
							))}
						</select>
					</label>
					{project.deviceMode === 'OFF' && (
						<p className="rounded-md bg-yellow-50 p-2 text-sm text-yellow-800 sm:col-span-2">
							This project doesn&apos;t record devices yet: turn on{' '}
							<strong>Devices</strong> in its SparkyTC settings.
						</p>
					)}
				</Form>
			)}

			{matrix && (
				<section className="card space-y-3" aria-label="Targets">
					<div className="flex flex-wrap items-baseline justify-between gap-2">
						<h2 className="font-semibold text-gray-900">Targets</h2>
						{matrix.targets > 0 && (
							<p role="status" className="text-sm text-gray-700">
								{matrix.covered} of {matrix.targets} tested
								{matrix.build ? ` in build ${matrix.build}` : ''}
							</p>
						)}
					</div>
					{targetRows.length === 0 ? (
						<p className="text-sm text-gray-500">
							No targets for this project yet.
							{isAdmin
								? ' Add the models (and versions) it has to be tested on below.'
								: ''}
						</p>
					) : (
						<CoverageTable rows={targetRows} isAdmin={isAdmin} busy={busy} />
					)}

					{isAdmin && project && (
						<Form
							ref={addForm}
							method="post"
							className="grid gap-3 border-t border-gray-200 pt-3 sm:grid-cols-4"
						>
							<input
								type="hidden"
								name="projectCode"
								value={project.projectShortCode}
							/>
							<label className="text-sm text-gray-700 sm:col-span-2">
								Model in stock
								<select name="known" defaultValue="" className="input mt-1">
									<option value="">Type it instead…</option>
									{knownModels.map((m) => (
										<option
											key={`${m.model}|${m.version ?? ''}`}
											value={`${m.model}|${m.version ?? ''}`}
										>
											{m.model}
											{m.version ? ` · ${m.version}` : ''}
										</option>
									))}
								</select>
							</label>
							<label className="text-sm text-gray-700">
								or model
								<input
									name="model"
									placeholder="e.g. Focus 3"
									className="input mt-1"
								/>
							</label>
							<label className="text-sm text-gray-700">
								Version
								<input
									name="version"
									placeholder="Any"
									className="input mt-1"
								/>
							</label>
							<label className="flex items-center gap-2 text-sm text-gray-700 sm:col-span-3">
								<input type="checkbox" name="anyVersion" /> Any version of the
								model counts
							</label>
							<button
								type="submit"
								name="intent"
								value="add"
								disabled={busy}
								className="btn-primary"
							>
								Add target
							</button>
						</Form>
					)}
					{result && 'error' in result && (
						<p
							role="alert"
							className="rounded-md bg-red-50 p-3 text-sm text-red-700"
						>
							{result.error}
						</p>
					)}
				</section>
			)}

			{otherRows.length > 0 && (
				<section className="card space-y-3" aria-label="Also tested">
					<h2 className="font-semibold text-gray-900">Also tested</h2>
					<p className="text-sm text-gray-600">
						Models and versions with results in this build that aren&apos;t
						targets.
					</p>
					<CoverageTable rows={otherRows} isAdmin={false} busy={busy} />
				</section>
			)}
		</main>
	)
}

function CoverageTable({
	rows,
	isAdmin,
	busy,
}: {
	rows: CoverageRow[]
	isAdmin: boolean
	busy: boolean
}) {
	return (
		<ul className="divide-y divide-gray-100">
			{rows.map((row) => (
				<li
					key={row.target?.id ?? `${row.model}|${row.version}`}
					className="flex flex-wrap items-center gap-x-4 gap-y-1 py-2 text-sm"
				>
					<span className="min-w-[10rem] font-medium text-gray-900">
						{row.model}
						<span className="ml-1 font-normal text-gray-500">
							{row.version ?? 'any version'}
						</span>
					</span>
					<span
						className={`rounded-full px-2 py-0.5 text-xs font-medium ${
							STATE[row.state].style
						}`}
					>
						{STATE[row.state].label}
					</span>
					<span className="text-gray-700">
						<Counts row={row} />
					</span>
					{row.devices.length > 0 && (
						<span className="font-mono text-xs text-gray-500">
							{row.devices.join(', ')}
						</span>
					)}
					{isAdmin && row.target && (
						<Form method="post" className="ml-auto">
							<input type="hidden" name="id" value={row.target.id} />
							<button
								type="submit"
								name="intent"
								value="delete"
								disabled={busy}
								aria-label={`Remove the target ${row.model} ${
									row.version ?? ''
								}`}
								className="text-xs text-red-600 hover:text-red-700"
							>
								Remove
							</button>
						</Form>
					)}
				</li>
			))}
		</ul>
	)
}
