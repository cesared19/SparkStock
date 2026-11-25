import { type ActionFunctionArgs, redirect } from '@remix-run/node'
import { apiRequest } from '~/utility/API.server'
import { destroySession, getSessionToken } from '~/utility/Session.server'

export async function action({ request }: ActionFunctionArgs) {
	const token = await getSessionToken(request)
	if (token) {
		await apiRequest('/auth/logout', { method: 'POST', token }).catch(
			() => undefined,
		)
	}
	return destroySession(request)
}

export function loader() {
	return redirect('/')
}
