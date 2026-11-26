import { type LoaderFunctionArgs } from '@remix-run/node'
import { API_URL } from '~/utility/API.server'
import { getSessionToken } from '~/utility/Session.server'

// a photo of a device's history, for people signed in to this site
export async function loader({ request, params }: LoaderFunctionArgs) {
	const token = await getSessionToken(request)
	if (!token || !/^\d+$/.test(params.id ?? ''))
		return new Response('Not found', { status: 404 })
	const response = await fetch(`${API_URL}/api/v1/event-photos/${params.id}`, {
		headers: { Authorization: `Bearer ${token}` },
	})
	if (!response.ok)
		return new Response('Not found', {
			status: response.status === 401 ? 401 : 404,
		})
	return new Response(response.body, {
		headers: {
			'Content-Type':
				response.headers.get('content-type') ?? 'application/octet-stream',
			'Cache-Control': 'private, max-age=86400',
			'X-Content-Type-Options': 'nosniff',
		},
	})
}
