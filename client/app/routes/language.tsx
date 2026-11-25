import { type ActionFunctionArgs, redirect } from '@remix-run/node'
import { LANG_COOKIE, parseLang } from '~/utility/Language'
import { safeRedirect } from '~/utility/Session.server'

// the language is each person's choice, kept for a year in their browser (like ProDash)
export async function action({ request }: ActionFunctionArgs) {
	const form = await request.formData()
	const lang = parseLang(String(form.get('lang') ?? '')) ?? 'en'
	const secure = process.env.COOKIE_SECURE === 'true' ? '; Secure' : ''
	return redirect(safeRedirect(form.get('redirectTo')), {
		headers: {
			'Set-Cookie': `${LANG_COOKIE}=${lang}; Path=/; Max-Age=31536000; SameSite=Lax${secure}`,
		},
	})
}

export function loader() {
	return redirect('/')
}
