import { createCookieSessionStorage, redirect } from '@remix-run/node'
import { randomBytes } from 'node:crypto'

// SparkyTC sessions last 7 days, and SparkStock signs in with SparkyTC
const SESSION_DURATION_SECONDS = 7 * 24 * 60 * 60

function sessionSecret() {
	if (process.env.SESSION_SECRET) {
		return process.env.SESSION_SECRET
	}
	console.warn(
		'SESSION_SECRET is not set, using a random secret. Users will be signed out whenever the client service restarts.',
	)
	return randomBytes(32).toString('hex')
}

export const sessionStorage = createCookieSessionStorage({
	cookie: {
		// not SparkyTC's __session: cookies are shared by every port of the same host, so the two apps would
		// overwrite each other's session
		name: '__sparkstock',
		httpOnly: true,
		sameSite: 'lax',
		path: '/',
		maxAge: SESSION_DURATION_SECONDS,
		secure: process.env.COOKIE_SECURE === 'true',
		secrets: [sessionSecret()],
	},
})

export async function getSessionToken(request: Request) {
	const session = await sessionStorage.getSession(request.headers.get('Cookie'))
	return session.get('token') as string | undefined
}

export async function createUserSession(token: string, redirectTo: string) {
	const session = await sessionStorage.getSession()
	session.set('token', token)
	return redirect(redirectTo, {
		headers: { 'Set-Cookie': await sessionStorage.commitSession(session) },
	})
}

export async function destroySession(request: Request, to = '/login') {
	const session = await sessionStorage.getSession(request.headers.get('Cookie'))
	return redirect(to, {
		headers: { 'Set-Cookie': await sessionStorage.destroySession(session) },
	})
}

/** Redirects to the login page and back to this page afterwards. */
export async function redirectToLogin(request: Request) {
	const url = new URL(request.url)
	const path = url.pathname.replace(/\.data$/, '') || '/'
	const back =
		path !== '/' && path !== '/login'
			? `?redirectTo=${encodeURIComponent(path + url.search)}`
			: ''
	return destroySession(request, `/login${back}`)
}

/** Only allows redirects inside SparkStock after signing in. */
export function safeRedirect(
	to: FormDataEntryValue | string | null | undefined,
) {
	if (
		!to ||
		typeof to !== 'string' ||
		!to.startsWith('/') ||
		to.startsWith('//') ||
		to.startsWith('/\\')
	) {
		return '/'
	}
	return to
}
