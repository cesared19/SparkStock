export type Lang = 'en' | 'es'

export const LANGS: { value: Lang; label: string }[] = [
	{ value: 'es', label: 'Español' },
	{ value: 'en', label: 'English' },
]

export const LANG_COOKIE = 'sparkstock_lang'

export function parseLang(value: string | null | undefined): Lang | undefined {
	if (!value) return undefined
	const first = value.trim().toLowerCase()
	if (first.startsWith('es')) return 'es'
	if (first.startsWith('en')) return 'en'
	return undefined
}

/**
 * The language of a request: the person's choice (a cookie in their browser), or else their browser's language,
 * like ProDash. Spanish or English; anything else is English.
 */
export function requestLang(request: Request): Lang {
	const cookie = request.headers.get('Cookie') ?? ''
	const chosen = cookie
		.split(';')
		.map((part) => part.trim().split('='))
		.find(([name]) => name === LANG_COOKIE)?.[1]
	const fromCookie = parseLang(chosen)
	if (fromCookie) return fromCookie
	const browser = (request.headers.get('Accept-Language') ?? '')
		.split(',')
		.map((part) => parseLang(part.split(';')[0]))
		.find(Boolean)
	return browser ?? 'en'
}
