/** The address people open SparkStock on, for QR labels: PUBLIC_URL, or the address of this request. */
export function publicURL(request: Request) {
	const configured = process.env.PUBLIC_URL?.trim().replace(/\/+$/, '')
	return configured || new URL(request.url).origin
}

export function deviceLink(request: Request, assetTag: string) {
	return `${publicURL(request)}/t/${encodeURIComponent(assetTag)}`
}
