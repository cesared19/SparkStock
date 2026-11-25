import type { DataResponse, User } from '~/models/types'
import { type Lang, requestLang } from './Language'
import { getSessionToken, redirectToLogin } from './Session.server'

export const API_URL = process.env.API_URL ?? 'http://localhost:8080'

export class UnauthorizedError extends Error {}

/** An error the backend explained (validation, conflict...), shown to the user as is. */
export class APIError extends Error {
	constructor(message: string, public status: number) {
		super(message)
	}
}

export async function apiRequest<T>(
	path: string,
	init: { method?: string; body?: unknown; token?: string; lang?: Lang } = {}
): Promise<T> {
	let response: Response
	try {
		response = await fetch(`${API_URL}/api/v1${path}`, {
			method: init.method ?? 'GET',
			headers: {
				'Content-Type': 'application/json',
				...(init.token ? { Authorization: `Bearer ${init.token}` } : {}),
				// the backend answers its messages in the person's language
				...(init.lang ? { 'Accept-Language': init.lang } : {}),
			},
			body: init.body === undefined ? undefined : JSON.stringify(init.body),
		})
	} catch (error) {
		console.error(`Unable to reach the SparkStock backend at ${API_URL}`, error)
		throw new Error(
			`Unable to reach the SparkStock backend (${API_URL}). Check that it is running and that API_URL points to it.`
		)
	}
	if (response.status === 401 && init.token) {
		throw new UnauthorizedError('Your session has expired, sign in again')
	}
	const json = await response.json().catch(() => ({}))
	if (!response.ok || json.status !== 'SUCCESS') {
		throw new APIError(
			json.message ?? `The request failed (${response.status})`,
			response.status
		)
	}
	return (json as DataResponse<T>).data
}

/** The API bound to the signed-in user's session; an expired session sends the user to the login page. */
export class API {
	public lang: Lang

	constructor(private request: Request, public token: string) {
		this.lang = requestLang(request)
	}

	private async call<T>(
		path: string,
		method: string,
		body?: unknown
	): Promise<T> {
		try {
			return await apiRequest<T>(path, { method, body, token: this.token, lang: this.lang })
		} catch (error) {
			if (error instanceof UnauthorizedError)
				throw await redirectToLogin(this.request)
			throw error
		}
	}

	get<T>(path: string) {
		return this.call<T>(path, 'GET')
	}
	post<T>(path: string, body?: unknown) {
		return this.call<T>(path, 'POST', body ?? {})
	}
	put<T>(path: string, body: unknown) {
		return this.call<T>(path, 'PUT', body)
	}
	delete<T>(path: string) {
		return this.call<T>(path, 'DELETE')
	}
}

export async function requireUser(request: Request) {
	const token = await getSessionToken(request)
	if (!token) throw await redirectToLogin(request)
	const api = new API(request, token)
	const user = await api.get<User>('/auth/me')
	return { api, user }
}

export async function requireAdmin(request: Request) {
	const result = await requireUser(request)
	if (result.user.role !== 'ADMIN') {
		throw new Response('Only admins can do this', { status: 403 })
	}
	return result
}

/** Runs an action and turns the backend's explanation of a refusal into `{ error }` for the form. */
export async function attempt<T>(
	run: () => Promise<T>
): Promise<T | { error: string }> {
	try {
		return await run()
	} catch (error) {
		if (error instanceof APIError) return { error: error.message }
		throw error
	}
}
