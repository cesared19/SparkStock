import { Form, useLocation } from '@remix-run/react'
import { useLang, useT } from '~/i18n'
import { LANGS } from '~/utility/Language'

/** English / Español, as two small buttons; the choice is saved in this browser. */
export default function LanguageSwitch({
	className = '',
}: {
	className?: string
}) {
	const t = useT()
	const lang = useLang()
	const location = useLocation()
	return (
		<Form
			method="post"
			action="/language"
			className={`inline-flex items-center gap-1 text-sm ${className}`}
		>
			<input
				type="hidden"
				name="redirectTo"
				value={location.pathname + location.search}
			/>
			<span className="sr-only">{t.nav.language}</span>
			{LANGS.map((option) => (
				<button
					key={option.value}
					type="submit"
					name="lang"
					value={option.value}
					aria-pressed={lang === option.value}
					lang={option.value}
					className={`rounded px-2 py-1 ${
						lang === option.value
							? 'bg-brand-50 font-semibold text-brand-700'
							: 'text-gray-600 hover:text-gray-900'
					}`}
				>
					{option.value.toUpperCase()}
				</button>
			))}
		</Form>
	)
}
