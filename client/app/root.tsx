import { type LoaderFunctionArgs } from '@remix-run/node'
import {
	Form,
	isRouteErrorResponse,
	Links,
	Meta,
	NavLink,
	Outlet,
	Scripts,
	ScrollRestoration,
	useLoaderData,
	useRouteError,
} from '@remix-run/react'
import { useEffect, useState } from 'react'
import type { User } from '~/models/types'
import LanguageSwitch from '~/components/LanguageSwitch'
import { useLang, useT } from '~/i18n'
import { requireUser } from '~/utility/API.server'
import { requestLang } from '~/utility/Language'

import './tailwind.css'

// SparkStock follows the system theme; applied before the first paint so dark mode doesn't flash white
const THEME_SCRIPT = `(function(){if(window.matchMedia('(prefers-color-scheme: dark)').matches){document.documentElement.classList.add('dark')}})()`

function useSystemTheme() {
	useEffect(() => {
		const query = window.matchMedia('(prefers-color-scheme: dark)')
		const apply = () =>
			document.documentElement.classList.toggle('dark', query.matches)
		apply()
		query.addEventListener('change', apply)
		return () => query.removeEventListener('change', apply)
	}, [])
}

export function Layout({ children }: { children: React.ReactNode }) {
	useSystemTheme()
	const lang = useLang()
	return (
		<html lang={lang} suppressHydrationWarning>
			<head>
				{/* eslint-disable-next-line react/no-danger */}
				<script dangerouslySetInnerHTML={{ __html: THEME_SCRIPT }} />
				<meta charSet="utf-8" />
				<meta name="viewport" content="width=device-width, initial-scale=1" />
				<title>SparkStock</title>
				<Meta />
				<Links />
			</head>
			<body>
				{children}
				<ScrollRestoration />
				<Scripts />
			</body>
		</html>
	)
}

export function links() {
	return [
		{ rel: 'icon', type: 'image/x-icon', href: '/favicon.ico' },
		{
			rel: 'icon',
			type: 'image/png',
			sizes: '32x32',
			href: '/favicon-32x32.png',
		},
		{ rel: 'apple-touch-icon', href: '/apple-touch-icon.png' },
	]
}

export async function loader({ request }: LoaderFunctionArgs) {
	const lang = requestLang(request)
	if (new URL(request.url).pathname === '/login') {
		return { user: undefined, sparkyTCUrl: '', lang }
	}
	const { user } = await requireUser(request)
	return { user, sparkyTCUrl: process.env.SPARKYTC_PUBLIC_URL ?? '', lang }
}

function TopNavigation({
	user,
	sparkyTCUrl,
}: {
	user: User
	sparkyTCUrl: string
}) {
	const t = useT()
	const [open, setOpen] = useState(false)
	const adminItems = [
		{ to: '/admin/types', label: t.nav.types, end: false },
		{ to: '/admin/locations', label: t.nav.locations, end: false },
		{ to: '/admin/import', label: t.nav.import, end: false },
	]
	const items = [
		{ to: '/', label: t.nav.dashboard, end: true },
		{ to: '/devices', label: t.nav.devices, end: false },
		{ to: '/reports', label: t.nav.reports, end: false },
		{ to: '/labels', label: t.nav.labels, end: false },
		{ to: '/coverage', label: t.nav.coverage, end: false },
		...(user.role === 'ADMIN' ? adminItems : []),
	]
	const linkClass = ({ isActive }: { isActive: boolean }) =>
		`block rounded-md px-3 py-2 text-sm font-medium ${
			isActive
				? 'bg-brand-50 text-brand-700'
				: 'text-gray-700 hover:bg-gray-100 hover:text-gray-900'
		}`
	return (
		<header className="border-b border-gray-200 bg-white">
			<div className="mx-auto flex max-w-6xl items-center gap-4 px-4 py-3">
				<NavLink to="/" className="text-lg font-bold text-brand-600">
					{t.appName}
				</NavLink>
				<nav
					aria-label={t.nav.main}
					className="hidden flex-1 flex-wrap gap-1 lg:flex"
				>
					{items.map((item) => (
						<NavLink
							key={item.to}
							to={item.to}
							end={item.end}
							className={linkClass}
						>
							{item.label}
						</NavLink>
					))}
				</nav>
				<div className="ml-auto hidden items-center gap-3 lg:flex">
					<LanguageSwitch />
					{sparkyTCUrl && (
						<a
							href={sparkyTCUrl}
							className="text-sm text-gray-600 hover:text-brand-600"
						>
							SparkyTC
						</a>
					)}
					<span className="text-sm text-gray-700">
						{user.displayName}
						{user.role === 'ADMIN' && (
							<span className="ml-1 text-xs text-gray-500">{t.nav.admin}</span>
						)}
					</span>
					<Form method="post" action="/logout">
						<button
							type="submit"
							className="text-sm text-gray-600 hover:text-brand-600"
						>
							{t.nav.signOut}
						</button>
					</Form>
				</div>
				<button
					type="button"
					className="ml-auto rounded-md border border-gray-300 px-3 py-1.5 text-sm lg:hidden"
					aria-expanded={open}
					aria-controls="mobile-menu"
					onClick={() => setOpen(!open)}
				>
					{t.nav.menu}
				</button>
			</div>
			{open && (
				<nav
					id="mobile-menu"
					aria-label={t.nav.main}
					className="space-y-1 border-t border-gray-200 px-4 py-3 lg:hidden"
				>
					{items.map((item) => (
						<NavLink
							key={item.to}
							to={item.to}
							end={item.end}
							className={linkClass}
							onClick={() => setOpen(false)}
						>
							{item.label}
						</NavLink>
					))}
					{sparkyTCUrl && (
						<a
							href={sparkyTCUrl}
							className="block px-3 py-2 text-sm text-gray-700"
						>
							SparkyTC
						</a>
					)}
					<div className="flex flex-wrap items-center justify-between gap-2 px-3 py-2">
						<Form method="post" action="/logout">
							<button type="submit" className="text-sm text-gray-700">
								{t.nav.signOutAs(user.displayName)}
							</button>
						</Form>
						<LanguageSwitch />
					</div>
				</nav>
			)}
		</header>
	)
}

export default function App() {
	const { user, sparkyTCUrl } = useLoaderData<typeof loader>()
	return (
		<div className="min-h-screen">
			{user && <TopNavigation user={user} sparkyTCUrl={sparkyTCUrl} />}
			<Outlet />
		</div>
	)
}

export function ErrorBoundary() {
	const t = useT()
	const error = useRouteError()
	const message = isRouteErrorResponse(error)
		? error.status === 403
			? t.errors.forbidden
			: error.status === 404
			? t.errors.notFound
			: `${error.status} ${error.statusText || error.data}`
		: error instanceof Error
		? error.message
		: t.errors.unexpected
	return (
		<div className="flex min-h-screen items-center justify-center p-6">
			<div className="max-w-md space-y-4 text-center">
				<h1 className="text-xl font-semibold text-gray-900">
					{t.errors.title}
				</h1>
				<p className="text-sm text-gray-600">{message}</p>
				<a href="/" className="text-sm text-brand-600 hover:underline">
					{t.errors.home}
				</a>
			</div>
		</div>
	)
}
