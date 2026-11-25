import { jsPDF } from 'jspdf'
import QRCode from 'qrcode'

export interface LabelSheet {
	id: string
	name: string
	format: 'a4' | 'letter'
	columns: number
	rows: number
	// all in millimetres
	labelWidth: number
	labelHeight: number
	marginTop: number
	marginLeft: number
	gapX: number
	gapY: number
}

/** Common adhesive label sheets. */
export const SHEETS: LabelSheet[] = [
	{
		id: 'a4-21',
		name: 'A4, 3 × 7 labels (63.5 × 38.1 mm, e.g. Avery L7160)',
		format: 'a4',
		columns: 3,
		rows: 7,
		labelWidth: 63.5,
		labelHeight: 38.1,
		marginTop: 15.15,
		marginLeft: 7.2,
		gapX: 2.5,
		gapY: 0,
	},
	{
		id: 'a4-24',
		name: 'A4, 3 × 8 labels (63.5 × 33.9 mm, e.g. Avery L7159)',
		format: 'a4',
		columns: 3,
		rows: 8,
		labelWidth: 63.5,
		labelHeight: 33.9,
		marginTop: 12.9,
		marginLeft: 6.5,
		gapX: 2.5,
		gapY: 0,
	},
	{
		id: 'letter-30',
		name: 'Letter, 3 × 10 labels (2⅝ × 1 in, e.g. Avery 5160)',
		format: 'letter',
		columns: 3,
		rows: 10,
		labelWidth: 66.675,
		labelHeight: 25.4,
		marginTop: 12.7,
		marginLeft: 4.7625,
		gapX: 3.175,
		gapY: 0,
	},
]

export interface LabelItem {
	assetTag: string
	name: string
	serialNumber?: string
	link: string
}

function fitText(doc: jsPDF, text: string, maxWidth: number) {
	if (doc.getTextWidth(text) <= maxWidth) return text
	let cut = text
	while (cut.length > 1 && doc.getTextWidth(`${cut}…`) > maxWidth)
		cut = cut.slice(0, -1)
	return `${cut}…`
}

/** Builds the PDF in the browser; `skip` leaves the first labels of the sheet empty (already used). */
export async function buildLabelsPDF(
	items: LabelItem[],
	sheet: LabelSheet,
	skip = 0,
) {
	const doc = new jsPDF({ unit: 'mm', format: sheet.format })
	const perPage = sheet.columns * sheet.rows
	const padding = 2.5
	for (let i = 0; i < items.length; i++) {
		const position = i + skip
		if (position > 0 && position % perPage === 0) doc.addPage()
		const slot = position % perPage
		const x =
			sheet.marginLeft +
			(slot % sheet.columns) * (sheet.labelWidth + sheet.gapX)
		const y =
			sheet.marginTop +
			Math.floor(slot / sheet.columns) * (sheet.labelHeight + sheet.gapY)
		const item = items[i]

		const qrSize = sheet.labelHeight - padding * 2
		const qr = await QRCode.toDataURL(item.link, {
			margin: 0,
			width: 300,
			errorCorrectionLevel: 'M',
		})
		doc.addImage(qr, 'PNG', x + padding, y + padding, qrSize, qrSize)

		const textX = x + padding * 2 + qrSize
		const textWidth = sheet.labelWidth - qrSize - padding * 3
		const small = sheet.labelHeight < 30
		doc.setFont('helvetica', 'bold')
		doc.setFontSize(small ? 11 : 13)
		doc.text(
			fitText(doc, item.assetTag, textWidth),
			textX,
			y + padding + (small ? 4 : 5),
		)
		doc.setFont('helvetica', 'normal')
		doc.setFontSize(small ? 7 : 8)
		const lines = [
			item.name,
			item.serialNumber ? `SN ${item.serialNumber}` : '',
		].filter(Boolean)
		lines.forEach((line, index) => {
			doc.text(
				fitText(doc, line, textWidth),
				textX,
				y + padding + (small ? 8.5 : 10.5) + index * (small ? 3.2 : 3.8),
			)
		})
		doc.setFontSize(6)
		doc.setTextColor(120)
		doc.text('SparkStock', textX, y + sheet.labelHeight - padding)
		doc.setTextColor(0)
	}
	return doc
}
