/** Parses CSV text (quotes, escaped quotes, line breaks in quotes, BOM); the delimiter is , ; or tab, guessed from the header. */
export function parseCSV(text: string): string[][] {
	const source = text.replace(/^\uFEFF/, '')
	const firstLine = source.split(/\r?\n/, 1)[0] ?? ''
	const delimiter = [',', ';', '\t'].reduce((best, d) =>
		firstLine.split(d).length > firstLine.split(best).length ? d : best,
	)
	const rows: string[][] = []
	let row: string[] = []
	let cell = ''
	let quoted = false
	for (let i = 0; i < source.length; i++) {
		const char = source[i]
		if (quoted) {
			if (char === '"' && source[i + 1] === '"') {
				cell += '"'
				i++
			} else if (char === '"') {
				quoted = false
			} else {
				cell += char
			}
		} else if (char === '"') {
			quoted = true
		} else if (char === delimiter) {
			row.push(cell)
			cell = ''
		} else if (char === '\n' || char === '\r') {
			if (char === '\r' && source[i + 1] === '\n') i++
			row.push(cell)
			rows.push(row)
			row = []
			cell = ''
		} else {
			cell += char
		}
	}
	if (cell !== '' || row.length > 0) {
		row.push(cell)
		rows.push(row)
	}
	return rows.filter((r) => r.some((c) => c.trim() !== ''))
}
