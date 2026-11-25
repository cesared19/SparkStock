import { type LoaderFunctionArgs } from '@remix-run/node'
import {
	Link,
	useLoaderData,
	useNavigate,
	useNavigation,
} from '@remix-run/react'
import { useRef, useState } from 'react'
import { Icon } from '~/components/ReportIcons'
import { type Dictionary, duration, useT } from '~/i18n'
import type { AlertLevel, DeviceStatus, Report } from '~/models/types'
import { requireUser } from '~/utility/API.server'
import { formatDate, toDate } from '~/utility/Format'
import { downloadCsv, exportPng } from '~/utility/ReportExport'
import styles from '~/report.css?url'

export const links = () => [{ rel: 'stylesheet', href: styles }]

const PERIODS = ['30', '90', '180', '365', 'all'] as const
type Period = (typeof PERIODS)[number]

export async function loader({ request }: LoaderFunctionArgs) {
	const { api } = await requireUser(request)
	const asked = new URL(request.url).searchParams.get('period') ?? '30'
	const period: Period = (PERIODS as readonly string[]).includes(asked)
		? (asked as Period)
		: '30'
	const report = await api.get<Report>(`/reports?period=${period}`)
	return { report, period }
}

const STATUS_TONE: Record<Exclude<DeviceStatus, 'RETIRED'>, string> = {
	AVAILABLE: 'tone-good',
	ON_LOAN: 'tone-open',
	ASSIGNED: 'cat-4',
	IN_REPAIR: 'tone-warning',
	LOST: 'tone-bad',
}
const STATUSES = Object.keys(STATUS_TONE) as Exclude<DeviceStatus, 'RETIRED'>[]

const LEVEL_TONE: Record<AlertLevel, string> = {
	VERY_OVERDUE: 'tone-bad',
	OVERDUE: 'tone-warning',
	ATTENTION: 'cat-3',
}

const pct = (part: number, total: number) =>
	total > 0 ? Math.round((part / total) * 100) : 0

function CardTitle({
	icon,
	title,
	onCsv,
}: {
	icon: React.ReactNode
	title: string
	onCsv?: () => void
}) {
	const t = useT()
	return (
		<h2 className="pd-card-title">
			<span className="pd-card-icon">{icon}</span>
			{title}
			{onCsv && (
				<button
					type="button"
					className="pd-csv"
					onClick={onCsv}
					data-no-export
					title={t.reports.csvTitle(title)}
				>
					<Icon.download />
					{t.reports.csv}
				</button>
			)}
		</h2>
	)
}

function ShowMore({
	total,
	shown,
	onToggle,
	expanded,
}: {
	total: number
	shown: number
	onToggle: () => void
	expanded: boolean
}) {
	const t = useT()
	if (total <= shown && !expanded) return null
	return (
		<button type="button" className="pd-more" onClick={onToggle} data-no-export>
			{expanded ? t.reports.showLess : t.reports.showMore(total - shown)}
		</button>
	)
}

function csvName(t: Dictionary, block: string) {
	return `${t.reports.fileName}-${block}-${new Date()
		.toISOString()
		.slice(0, 10)}.csv`
}

export default function Reports() {
	const t = useT()
	const { report, period } = useLoaderData<typeof loader>()
	const navigate = useNavigate()
	const loading = useNavigation().state === 'loading'
	const reportRef = useRef<HTMLDivElement>(null)
	const [creating, setCreating] = useState(false)
	const [error, setError] = useState<string>()

	async function downloadPng() {
		if (!reportRef.current) return
		setCreating(true)
		setError(undefined)
		try {
			await exportPng(
				reportRef.current,
				`${t.reports.fileName}-${new Date().toISOString().slice(0, 10)}.png`,
				t.reports.pngError
			)
		} catch (e) {
			setError(e instanceof Error ? e.message : t.reports.pngError)
		} finally {
			setCreating(false)
		}
	}

	const { stock, usage, alerts } = report
	const late = alerts.overdue + alerts.veryOverdue
	const health = late > 0 ? 'bad' : alerts.attention > 0 ? 'warning' : 'good'

	return (
		<div className="pd pd-page">
			<div className="pd-toolbar">
				<label className="sr-only" htmlFor="period">
					{t.reports.period}
				</label>
				<select
					id="period"
					className="pd-select"
					value={period}
					onChange={(e) =>
						navigate(`?period=${e.target.value}`, { replace: true })
					}
				>
					{PERIODS.map((p) => (
						<option key={p} value={p}>
							{t.reports.periods[p]}
						</option>
					))}
				</select>
				<button
					type="button"
					className="pd-btn-primary"
					onClick={downloadPng}
					disabled={creating}
				>
					<Icon.image />
					{creating ? t.reports.creating : t.reports.downloadPng}
				</button>
			</div>
			{error && (
				<p
					role="alert"
					className="pd-report tone-bad-text"
					style={{ paddingBottom: 12 }}
				>
					{error}
				</p>
			)}

			<div ref={reportRef} className="pd pd-report" aria-busy={loading}>
				<div className="pd-head">
					<h1>
						{t.appName} · {t.reports.title}
					</h1>
					<p>
						{t.reports.reportLine(
							formatDate(report.to, t.locale),
							t.reports.periods[period]
						)}
					</p>
				</div>

				<div className="pd-blocks">
					{/* ---- tiles ---- */}
					<section className="pd-block pd-quarter">
						<div className="pd-tile tone-open">
							<div className="pd-tile-top">
								<span className="pd-tile-icon">
									<Icon.box />
								</span>
							</div>
							<div className="pd-tile-value">{stock.total}</div>
							<div className="pd-tile-label">{t.reports.inStock}</div>
							<div className="pd-tile-hint">
								{t.reports.inStockHint(stock.retired)}
							</div>
						</div>
					</section>
					<section className="pd-block pd-quarter">
						<div className="pd-tile tone-closed">
							<div className="pd-tile-top">
								<span className="pd-tile-icon">
									<Icon.handoff />
								</span>
							</div>
							<div className="pd-tile-value">{stock.byStatus.ON_LOAN ?? 0}</div>
							<div className="pd-tile-label">{t.reports.onLoan}</div>
							<div className="pd-tile-hint">
								{t.reports.onLoanHint(stock.byStatus.ASSIGNED ?? 0)}
							</div>
						</div>
					</section>
					<section className="pd-block pd-quarter">
						<div className={`pd-tile tone-${health}`}>
							<div className="pd-tile-top">
								<span className="pd-tile-icon">
									{late > 0 ? <Icon.alert /> : <Icon.check />}
								</span>
								<span className="pd-badge">
									{health === 'bad' ? (
										<Icon.alert />
									) : health === 'warning' ? (
										<Icon.warning />
									) : (
										<Icon.check />
									)}
									{t.reports.health[health]}
								</span>
							</div>
							<div className="pd-tile-value">{late}</div>
							<div className="pd-tile-label">
								{late > 0 ? t.reports.notReturned : t.reports.allReturned}
							</div>
							<div className="pd-tile-hint">
								{t.reports.notReturnedHint(alerts.attention)}
							</div>
						</div>
					</section>
					<section className="pd-block pd-quarter">
						<div className="pd-tile cat-1">
							<div className="pd-tile-top">
								<span className="pd-tile-icon">
									<Icon.gauge />
								</span>
							</div>
							<div className="pd-tile-value">
								{usage.averageUtilization === undefined ||
								usage.averageUtilization === null
									? t.none
									: Math.round(usage.averageUtilization * 100)}
								<span className="pd-tile-unit">%</span>
							</div>
							<div className="pd-tile-label">{t.reports.utilization}</div>
							<div className="pd-tile-hint">
								{t.reports.utilizationHint(usage.unused)}
							</div>
						</div>
					</section>

					{/* ---- stock by status ---- */}
					<section className="pd-block pd-full">
						<div className="pd-card">
							<CardTitle
								icon={<Icon.grid />}
								title={t.reports.stockByStatus}
								onCsv={() =>
									downloadCsv(
										csvName(t, 'stock'),
										t.reports.csvHeaders.stock,
										stock.byType.flatMap((type) =>
											STATUSES.filter((s) => (type.byStatus[s] ?? 0) > 0).map(
												(s) => [type.name, t.status[s], type.byStatus[s]]
											)
										)
									)
								}
							/>
							<div
								className="pd-stack"
								role="img"
								aria-label={t.reports.stockByStatus}
							>
								{STATUSES.filter((s) => stock.byStatus[s] > 0).map((s) => (
									<div
										key={s}
										className={`pd-seg ${STATUS_TONE[s]}`}
										style={{ flex: stock.byStatus[s] }}
									/>
								))}
							</div>
							<div className="pd-status-grid">
								{STATUSES.map((s) => (
									<Link
										key={s}
										to={`/devices?status=${s}`}
										className={`pd-status-tile ${STATUS_TONE[s]}`}
									>
										<span className="pd-status-value">
											{stock.byStatus[s] ?? 0}
										</span>
										<span className="pd-status-pct">
											{pct(stock.byStatus[s] ?? 0, stock.total)}%
										</span>
										<span className="pd-status-name">{t.status[s]}</span>
									</Link>
								))}
							</div>
						</div>
					</section>

					{/* ---- not returned ---- */}
					<AlertsBlock report={report} />

					{/* ---- loan time ---- */}
					<LoanTimeBlock report={report} />

					{/* ---- by type and location ---- */}
					<section className="pd-block pd-half">
						<div className="pd-card">
							<CardTitle icon={<Icon.layers />} title={t.reports.byType} />
							<p className="pd-help">{t.reports.byTypeHelp}</p>
							{stock.byType.map((type) => (
								<div key={type.typeId} className="pd-type-row">
									<div className="pd-type-head">
										<strong>
											{type.name}{' '}
											<span className="pd-muted">({t.usage[type.usage]})</span>
										</strong>
										<span className="pd-type-counts">
											{type.total} {t.reports.total}
										</span>
									</div>
									<div className="pd-stack pd-stack-thin">
										{STATUSES.filter((s) => (type.byStatus[s] ?? 0) > 0).map(
											(s) => (
												<div
													key={s}
													className={`pd-seg ${STATUS_TONE[s]}`}
													style={{ flex: type.byStatus[s] }}
													title={`${t.status[s]}: ${type.byStatus[s]}`}
												/>
											)
										)}
									</div>
								</div>
							))}
							{stock.byLocation.length > 0 && (
								<>
									<h3 className="pd-subtitle">{t.reports.byLocation}</h3>
									{stock.byLocation.map((location) => (
										<div
											key={location.name ?? '-'}
											className="pd-compare-row"
											style={{ padding: '4px 0' }}
										>
											<div className="pd-bar pd-bar-small">
												<div
													className="pd-fill tone-todo"
													style={{
														width: `${pct(location.count, stock.total)}%`,
													}}
												/>
											</div>
											<span
												className="pd-type-counts"
												style={{ minWidth: '9rem', textAlign: 'right' }}
											>
												{location.name ?? t.reports.noLocation} ·{' '}
												{location.count}
											</span>
										</div>
									))}
								</>
							)}
						</div>
					</section>

					{/* ---- use and disuse ---- */}
					<UsageBlock report={report} />

					{/* ---- people ---- */}
					<PeopleBlock report={report} />
				</div>
			</div>
		</div>
	)
}

function AlertsBlock({ report }: { report: Report }) {
	const t = useT()
	const [expanded, setExpanded] = useState(false)
	const { alerts } = report.alerts
	const SHOWN = 6
	const visible = expanded ? alerts : alerts.slice(0, SHOWN)
	const scale = Math.max(2, ...alerts.map((a) => a.ratio)) * 1.05
	return (
		<section className="pd-block pd-full">
			<div className="pd-card">
				<CardTitle
					icon={<Icon.hourglass />}
					title={t.reports.alertsTitle}
					onCsv={() =>
						downloadCsv(
							csvName(t, 'alerts'),
							t.reports.csvHeaders.alerts,
							alerts.map((a) => [
								t.reports.levels[a.level],
								a.device.assetTag,
								a.device.name,
								a.holder?.displayName,
								toDate(a.heldSince).toISOString(),
								toDate(a.dueAt).toISOString(),
								a.heldHours,
								a.plannedHours,
								a.ratio,
								a.overdueHours,
							])
						)
					}
				/>
				{alerts.length === 0 ? (
					<p className="pd-ok">
						<Icon.check />
						{t.reports.alertsNone}
					</p>
				) : (
					<>
						<div className="pd-summary">
							<span className="pd-number">{alerts.length}</span>
							<span className="pd-summary-label">
								{t.reports.alertsSummary(alerts.length)}
							</span>
						</div>
						<div className="pd-legend">
							{(['VERY_OVERDUE', 'OVERDUE', 'ATTENTION'] as AlertLevel[]).map(
								(level) => (
									<span key={level}>
										<span className={`pd-sev ${LEVEL_TONE[level]}`}>
											{t.reports.levels[level]}
										</span>
										{t.reports.levelHelp[level]}
									</span>
								)
							)}
						</div>
						<div className="pd-scale">
							<span className="pd-tick" />
							{t.reports.scale}
						</div>
						<ul className="pd-list">
							{visible.map((a) => (
								<li key={a.device.id} className="pd-row">
									<div className="pd-row-head">
										<span className={`pd-sev ${LEVEL_TONE[a.level]}`}>
											{t.reports.levels[a.level]}
										</span>
										<span className="pd-key">
											<Link to={`/devices/${a.device.id}`}>
												{a.device.assetTag}
											</Link>
										</span>
										<span>{a.device.name}</span>
										{a.holder && (
											<span className="pd-muted">
												· {t.reports.with(a.holder.displayName)}
											</span>
										)}
									</div>
									<div className="pd-bar-row">
										<div className="pd-bar">
											<div
												className={`pd-fill ${LEVEL_TONE[a.level]}`}
												style={{
													width: `${Math.min(100, (a.ratio / scale) * 100)}%`,
												}}
											/>
											<div
												className="pd-marker"
												style={{ left: `${(1 / scale) * 100}%` }}
											/>
										</div>
										<span className="pd-big">×{a.ratio}</span>
									</div>
									<div className="pd-time">
										<span>
											{t.reports.heldFor}{' '}
											<strong>{duration(t, a.heldHours)}</strong>
										</span>
										<span>
											· {t.reports.asked(duration(t, a.plannedHours))}
										</span>
										<span
											className={
												a.overdueHours > 0
													? 'tone-bad-text'
													: 'tone-warning-text'
											}
										>
											·{' '}
											{a.overdueHours > 0
												? t.reports.lateBy(duration(t, a.overdueHours))
												: t.reports.dueIn(
														duration(t, (a.dueAt - report.to) / 3600)
												  )}
										</span>
									</div>
								</li>
							))}
						</ul>
						<ShowMore
							total={alerts.length}
							shown={SHOWN}
							expanded={expanded}
							onToggle={() => setExpanded(!expanded)}
						/>
					</>
				)}
				<p className="pd-note">
					<Icon.warning />
					{t.reports.escalation(report.alerts.escalateAfterDays)}
				</p>
			</div>
		</section>
	)
}

function LoanTimeBlock({ report }: { report: Report }) {
	const t = useT()
	const [expanded, setExpanded] = useState(false)
	const { loans } = report
	const SHOWN = 5
	const devices = expanded ? loans.byDevice : loans.byDevice.slice(0, SHOWN)
	const maxHours = Math.max(1, ...loans.byDevice.map((d) => d.hours))
	const average = loans.averageHours ?? 0
	const asked = loans.averagePlannedHours ?? 0
	const compareMax = Math.max(average, asked, 1)
	return (
		<section className="pd-block pd-half">
			<div className="pd-card">
				<CardTitle
					icon={<Icon.clock />}
					title={t.reports.loanTime}
					onCsv={() =>
						downloadCsv(
							csvName(t, 'loans'),
							t.reports.csvHeaders.loans,
							loans.byDevice.map((d) => [
								d.device.assetTag,
								d.device.name,
								d.loans,
								d.hours,
							])
						)
					}
				/>
				<div className="pd-kpis">
					<div className="pd-kpi">
						<strong>{loans.started}</strong>
						<span>{t.reports.loansStarted}</span>
					</div>
					<div className="pd-kpi">
						<strong>{loans.returned}</strong>
						<span>{t.reports.returned}</span>
					</div>
					<div className="pd-kpi">
						<strong
							className={loans.returnedLate > 0 ? 'tone-bad-text' : undefined}
						>
							{loans.returnedLate}
						</strong>
						<span>{t.reports.returnedLate}</span>
					</div>
				</div>
				{loans.averageHours !== undefined && loans.averageHours !== null && (
					<div className="pd-compare">
						<span className="pd-compare-label">{t.reports.averageLoan}</span>
						<div className="pd-compare-row">
							<div className="pd-bar">
								<div
									className={`pd-fill ${
										asked > 0 && average > asked ? 'tone-bad' : 'tone-closed'
									}`}
									style={{ width: `${(average / compareMax) * 100}%` }}
								/>
							</div>
							<span className="pd-compare-value">{duration(t, average)}</span>
						</div>
						{asked > 0 && (
							<>
								<span className="pd-compare-label">{t.reports.askedLoan}</span>
								<div className="pd-compare-row">
									<div className="pd-bar">
										<div
											className="pd-fill tone-todo"
											style={{ width: `${(asked / compareMax) * 100}%` }}
										/>
									</div>
									<span className="pd-compare-value">{duration(t, asked)}</span>
								</div>
							</>
						)}
						{loans.medianHours !== undefined && loans.medianHours !== null && (
							<span className="pd-muted">
								{t.reports.medianLoan} {duration(t, loans.medianHours)}
							</span>
						)}
					</div>
				)}
				{loans.longest && (
					<p className="pd-time">
						<span>{t.reports.longest}:</span>
						<span className="pd-key">
							<Link to={`/devices/${loans.longest.device.id}`}>
								{loans.longest.device.assetTag}
							</Link>
						</span>
						<strong>{duration(t, loans.longest.hours)}</strong>
						{loans.longest.person && (
							<span>· {loans.longest.person.displayName}</span>
						)}
						{loans.longest.active && (
							<span className="tone-warning-text">· {t.reports.stillOut}</span>
						)}
					</p>
				)}
				<h3 className="pd-subtitle">{t.reports.mostUsedDevices}</h3>
				{loans.byDevice.length === 0 ? (
					<p className="pd-muted">{t.reports.noLoans}</p>
				) : (
					<ul className="pd-list">
						{devices.map((d) => (
							<li
								key={d.device.id}
								className="pd-row"
								style={{ padding: '10px 0' }}
							>
								<div className="pd-row-head">
									<span className="pd-key">
										<Link to={`/devices/${d.device.id}`}>
											{d.device.assetTag}
										</Link>
									</span>
									<span>{d.device.name}</span>
									<span className="pd-muted">· {t.reports.loans(d.loans)}</span>
								</div>
								<div
									className="pd-bar-row"
									style={{ gridTemplateColumns: '1fr 6.5rem' }}
								>
									<div className="pd-bar pd-bar-small">
										<div
											className="pd-fill tone-open"
											style={{ width: `${(d.hours / maxHours) * 100}%` }}
										/>
									</div>
									<span className="pd-mid">{duration(t, d.hours)}</span>
								</div>
							</li>
						))}
					</ul>
				)}
				<ShowMore
					total={loans.byDevice.length}
					shown={SHOWN}
					expanded={expanded}
					onToggle={() => setExpanded(!expanded)}
				/>
			</div>
		</section>
	)
}

function UsageBlock({ report }: { report: Report }) {
	const t = useT()
	const [expanded, setExpanded] = useState(false)
	const { usage } = report
	const used = usage.devices.filter((d) => !d.unused)
	const unused = usage.devices.filter((d) => d.unused)
	const SHOWN = 8
	const visible = expanded ? used : used.slice(0, SHOWN)
	const tone = (u: number) =>
		u >= 0.5 ? 'tone-good' : u >= 0.15 ? 'tone-open' : 'tone-warning'
	return (
		<section className="pd-block pd-full">
			<div className="pd-card">
				<CardTitle
					icon={<Icon.gauge />}
					title={t.reports.usage}
					onCsv={() =>
						downloadCsv(
							csvName(t, 'usage'),
							t.reports.csvHeaders.usage,
							usage.devices.map((d) => [
								d.device.assetTag,
								d.device.name,
								d.device.type,
								t.status[d.status],
								Math.round(d.utilization * 1000) / 10,
								d.hours,
								d.loans,
								d.lastLoanAt ? toDate(d.lastLoanAt).toISOString() : '',
								d.unused ? t.yes : t.no,
							])
						)
					}
				/>
				<p className="pd-help">{t.reports.usageHelp}</p>
				{usage.devices.length === 0 ? (
					<p className="pd-muted">{t.reports.noLentDevices}</p>
				) : (
					<>
						<ul className="pd-list">
							{visible.map((d) => (
								<li
									key={d.device.id}
									className="pd-row"
									style={{ padding: '10px 0' }}
								>
									<div className="pd-row-head">
										<span className="pd-key">
											<Link to={`/devices/${d.device.id}`}>
												{d.device.assetTag}
											</Link>
										</span>
										<span>{d.device.name}</span>
										<span className="pd-muted">
											· {t.reports.loans(d.loans)} · {duration(t, d.hours)}
										</span>
									</div>
									<div className="pd-bar-row">
										<div className="pd-bar pd-bar-small">
											<div
												className={`pd-fill ${tone(d.utilization)}`}
												style={{ width: `${d.utilization * 100}%` }}
											/>
										</div>
										<span className="pd-mid">
											{Math.round(d.utilization * 100)}%
										</span>
									</div>
								</li>
							))}
						</ul>
						<ShowMore
							total={used.length}
							shown={SHOWN}
							expanded={expanded}
							onToggle={() => setExpanded(!expanded)}
						/>

						<h3 className="pd-subtitle">
							{t.reports.unusedTitle}{' '}
							{unused.length > 0 && (
								<span className="pd-chip tone-warning">{unused.length}</span>
							)}
						</h3>
						{unused.length === 0 ? (
							<p className="pd-ok">
								<Icon.check />
								{t.reports.unusedNone}
							</p>
						) : (
							<ul className="pd-list">
								{unused.map((d) => (
									<li
										key={d.device.id}
										className="pd-row"
										style={{ padding: '8px 0' }}
									>
										<div className="pd-row-head">
											<span className="pd-key">
												<Link to={`/devices/${d.device.id}`}>
													{d.device.assetTag}
												</Link>
											</span>
											<span>{d.device.name}</span>
											<span className="pd-muted">
												·{' '}
												{d.lastLoanAt
													? t.reports.lastLoan(
															duration(t, (report.to - d.lastLoanAt) / 3600)
													  )
													: t.reports.neverLent}
											</span>
										</div>
									</li>
								))}
							</ul>
						)}
					</>
				)}
			</div>
		</section>
	)
}

function PeopleBlock({ report }: { report: Report }) {
	const t = useT()
	const people = report.loans.byPerson.slice(0, 8)
	const maxHours = Math.max(1, ...people.map((p) => p.hours))
	return (
		<section className="pd-block pd-half">
			<div className="pd-card">
				<CardTitle
					icon={<Icon.users />}
					title={t.reports.topPeople}
					onCsv={() =>
						downloadCsv(
							csvName(t, 'people'),
							t.reports.csvHeaders.people,
							report.loans.byPerson.map((p) => [
								p.person.displayName,
								p.loans,
								p.hours,
								p.returnedLate,
							])
						)
					}
				/>
				{people.length === 0 ? (
					<p className="pd-muted">{t.reports.noLoans}</p>
				) : (
					<ul className="pd-list">
						{people.map((p) => (
							<li
								key={p.person.userID}
								className="pd-row"
								style={{ padding: '10px 0' }}
							>
								<div className="pd-row-head">
									<strong>{p.person.displayName}</strong>
									<span className="pd-muted">· {t.reports.loans(p.loans)}</span>
									{p.returnedLate > 0 && (
										<span className="pd-chip tone-bad">
											{t.reports.late(p.returnedLate)}
										</span>
									)}
								</div>
								<div
									className="pd-bar-row"
									style={{ gridTemplateColumns: '1fr 6.5rem' }}
								>
									<div className="pd-bar pd-bar-small">
										<div
											className="pd-fill tone-closed"
											style={{ width: `${(p.hours / maxHours) * 100}%` }}
										/>
									</div>
									<span className="pd-mid">{duration(t, p.hours)}</span>
								</div>
							</li>
						))}
					</ul>
				)}
			</div>
		</section>
	)
}
