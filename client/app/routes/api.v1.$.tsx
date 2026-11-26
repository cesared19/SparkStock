import {
	type ActionFunctionArgs,
	type LoaderFunctionArgs,
} from '@remix-run/node'
import { API_URL } from '~/utility/API.server'

// The phone app (SparkStock Scanner) calls the API on SparkStock's own address, e.g. http://<server>:3100/api/v1/...:
// the backend is only published on the server itself. This forwards those requests as they are, with the app's
// token (Authorization) and language, never this site's session cookie.
const FORWARDED_HEADERS = [
	'authorization',
	'content-type',
	'accept',
	'accept-language',
]
const RETURNED_HEADERS = [
	'content-type',
	'cache-control',
	'x-content-type-options',
]

async function forward(request: Request, splat: string | undefined) {
	const path = splat ?? ''
	// only plain paths under /api/v1: no "..", no encoded slashes or backslashes
	if (!/^[A-Za-z0-9._~\-/]*$/.test(path) || path.split('/').includes('..')) {
		return Response.json(
			{ status: 'ERROR', error: 'Not Found', message: 'Not found' },
			{ status: 404 }
		)
	}
	const url = new URL(request.url)
	const headers = new Headers()
	for (const name of FORWARDED_HEADERS) {
		const value = request.headers.get(name)
		if (value) headers.set(name, value)
	}
	const hasBody = request.method !== 'GET' && request.method !== 'HEAD'
	let response: Response
	try {
		response = await fetch(`${API_URL}/api/v1/${path}${url.search}`, {
			method: request.method,
			headers,
			body: hasBody ? request.body : undefined,
			// streams the body (e.g. a photo) instead of reading it whole
			...(hasBody ? { duplex: 'half' } : {}),
		} as RequestInit)
	} catch {
		return Response.json(
			{
				status: 'ERROR',
				error: 'Unavailable',
				message: 'SparkStock is restarting, try again in a moment',
			},
			{ status: 503 }
		)
	}
	const returned = new Headers()
	for (const name of RETURNED_HEADERS) {
		const value = response.headers.get(name)
		if (value) returned.set(name, value)
	}
	return new Response(response.body, {
		status: response.status,
		headers: returned,
	})
}

export const loader = ({ request, params }: LoaderFunctionArgs) =>
	forward(request, params['*'])
export const action = ({ request, params }: ActionFunctionArgs) =>
	forward(request, params['*'])
