import {
	type ActionFunctionArgs,
	type LoaderFunctionArgs,
	redirect,
} from '@remix-run/node'
import {
	Form,
	useActionData,
	useNavigation,
	useSearchParams,
} from '@remix-run/react'
import LanguageSwitch from '~/components/LanguageSwitch'
import { useT } from '~/i18n'
import type { LoginResponse } from '~/models/types'
import { APIError, apiRequest } from '~/utility/API.server'
import { requestLang } from '~/utility/Language'
import {
	createUserSession,
	getSessionToken,
	safeRedirect,
} from '~/utility/Session.server'

export async function loader({ request }: LoaderFunctionArgs) {
	if (await getSessionToken(request)) return redirect('/')
	return null
}

export async function action({ request }: ActionFunctionArgs) {
	const form = await request.formData()
	const username = String(form.get('username') ?? '')
	const password = String(form.get('password') ?? '')
	try {
		const login = await apiRequest<LoginResponse>('/auth/login', {
			method: 'POST',
			body: { username, password },
			lang: requestLang(request),
		})
		return createUserSession(login.token, safeRedirect(form.get('redirectTo')))
	} catch (error) {
		if (error instanceof APIError) return { error: error.message }
		throw error
	}
}

export default function Login() {
	const t = useT()
	const result = useActionData<typeof action>()
	const [params] = useSearchParams()
	const busy = useNavigation().state !== 'idle'
	return (
		<main className="flex min-h-screen items-center justify-center p-4">
			<div className="w-full max-w-sm space-y-3">
				<Form
					method="post"
					className="space-y-4 rounded-lg border border-gray-200 bg-white p-6 shadow-sm"
				>
					<div>
						<h1 className="text-2xl font-bold text-brand-600">{t.appName}</h1>
						<p className="mt-1 text-sm text-gray-600">{t.login.intro}</p>
					</div>
					<input
						type="hidden"
						name="redirectTo"
						value={params.get('redirectTo') ?? '/'}
					/>
					<label className="block text-sm font-medium text-gray-700">
						{t.login.username}
						<input
							name="username"
							autoComplete="username"
							required
							className="input mt-1"
						/>
					</label>
					<label className="block text-sm font-medium text-gray-700">
						{t.login.password}
						<input
							name="password"
							type="password"
							autoComplete="current-password"
							required
							className="input mt-1"
						/>
					</label>
					{result?.error && (
						<p
							role="alert"
							className="rounded-md bg-red-50 p-2 text-sm text-red-700"
						>
							{result.error}
						</p>
					)}
					<button type="submit" disabled={busy} className="btn-primary w-full">
						{busy ? t.login.signingIn : t.login.signIn}
					</button>
				</Form>
				<div className="text-center">
					<LanguageSwitch />
				</div>
			</div>
		</main>
	)
}
