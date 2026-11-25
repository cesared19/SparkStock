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
import { requireUser } from '~/utility/API.server'

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
	return (
		<html lang="en" suppressHydrationWarning>
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
	if (new URL(request.url).pathname === '/login') {
		return { user: undefined, sparkyTCUrl: '' }
	}
	const { user } = await requireUser(request)
	return { user, sparkyTCUrl: process.env.SPARKYTC_PUBLIC_URL ?? '' }
}

function TopNavigation({
	user,
	sparkyTCUrl,
}: {
	user: User
	sparkyTCUrl: string
}) {
	const [open, setOpen] = useState(false)
	const adminItems = [
		{ to: '/admin/types', label: 'Device types', end: false },
		{ to: '/admin/locations', label: 'Locations', end: false },
		{ to: '/admin/import', label: 'Import', end: false },
	]
	const items = [
		{ to: '/', label: 'Dashboard', end: true },
		{ to: '/devices', label: 'Devices', end: false },
		{ to: '/labels', label: 'Labels', end: false },
		{ to: '/coverage', label: 'Coverage', end: false },
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
					SparkStock
				</NavLink>
				<nav aria-label="Main" className="hidden flex-1 gap-1 md:flex">
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
				<div className="ml-auto hidden items-center gap-3 md:flex">
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
							<span className="ml-1 text-xs text-gray-500">(admin)</span>
						)}
					</span>
					<Form method="post" action="/logout">
						<button
							type="submit"
							className="text-sm text-gray-600 hover:text-brand-600"
						>
							Sign out
						</button>
					</Form>
				</div>
				<button
					type="button"
					className="ml-auto rounded-md border border-gray-300 px-3 py-1.5 text-sm md:hidden"
					aria-expanded={open}
					aria-controls="mobile-menu"
					onClick={() => setOpen(!open)}
				>
					Menu
				</button>
			</div>
			{open && (
				<nav
					id="mobile-menu"
					aria-label="Main"
					className="space-y-1 border-t border-gray-200 px-4 py-3 md:hidden"
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
					<Form method="post" action="/logout" className="px-3 py-2">
						<button type="submit" className="text-sm text-gray-700">
							Sign out ({user.displayName})
						</button>
					</Form>
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
	const error = useRouteError()
	const message = isRouteErrorResponse(error)
		? error.status === 403
			? 'You do not have permission to view this page.'
			: error.status === 404
			? 'This page or device does not exist.'
			: `${error.status} ${error.statusText || error.data}`
		: error instanceof Error
		? error.message
		: 'An unexpected error occurred'
	return (
		<div className="flex min-h-screen items-center justify-center p-6">
			<div className="max-w-md space-y-4 text-center">
				<h1 className="text-xl font-semibold text-gray-900">
					Something went wrong
				</h1>
				<p className="text-sm text-gray-600">{message}</p>
				<a href="/" className="text-sm text-brand-600 hover:underline">
					Go to the dashboard
				</a>
			</div>
		</div>
	)
}
