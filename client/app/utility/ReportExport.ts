import { toBlob } from 'html-to-image'

export const REPORT_WIDTH = 1200

function save(blob: Blob, fileName: string) {
	const url = URL.createObjectURL(blob)
	const a = document.createElement('a')
	a.href = url
	a.download = fileName
	document.body.appendChild(a)
	a.click()
	a.remove()
	setTimeout(() => URL.revokeObjectURL(url), 10_000)
}

/**
 * The report as a PNG, like ProDash's: rendered at REPORT_WIDTH whatever the screen, at twice the resolution, without
 * the parts marked data-no-export (buttons). On phones it opens the share sheet when it can.
 */
export async function exportPng(
	node: HTMLElement,
	fileName: string,
	errorMessage: string
) {
	node.classList.add('exporting')
	try {
		const blob = await toBlob(node, {
			pixelRatio: 2,
			width: REPORT_WIDTH,
			height: node.scrollHeight,
			style: { width: `${REPORT_WIDTH}px` },
			backgroundColor: getComputedStyle(node).backgroundColor,
			filter: (n) => !(n instanceof HTMLElement && n.dataset.noExport != null),
		})
		if (!blob) throw new Error(errorMessage)
		const file = new File([blob], fileName, { type: 'image/png' })
		const touch = matchMedia('(pointer: coarse)').matches
		if (touch && navigator.canShare?.({ files: [file] })) {
			try {
				await navigator.share({ files: [file], title: fileName })
				return
			} catch (e) {
				if ((e as Error).name === 'AbortError') return
			}
		}
		save(blob, fileName)
	} finally {
		node.classList.remove('exporting')
	}
}

function cell(value: string | number | boolean | null | undefined) {
	const text = value === null || value === undefined ? '' : String(value)
	return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

/** A CSV file (UTF-8 with a byte order mark, so Excel reads accents right). */
export function downloadCsv(
	fileName: string,
	header: string[],
	rows: (string | number | boolean | null | undefined)[][]
) {
	const text = [header, ...rows]
		.map((row) => row.map(cell).join(','))
		.join('\r\n')
	save(new Blob(['﻿' + text], { type: 'text/csv;charset=utf-8' }), fileName)
}
