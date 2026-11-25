import { Form, useNavigation } from '@remix-run/react'
import { useState } from 'react'
import { useT } from '~/i18n'
import type {
	Device,
	DeviceType,
	FieldDefinition,
	Location,
} from '~/models/types'

function FieldInput({
	field,
	value,
}: {
	field: FieldDefinition
	value: unknown
}) {
	const t = useT()
	const name = `field.${field.key}`
	const text = value === undefined || value === null ? '' : String(value)
	switch (field.kind) {
		case 'BOOLEAN':
			return (
				<select
					name={name}
					defaultValue={
						value === true ? 'true' : value === false ? 'false' : ''
					}
					className="input mt-1"
				>
					<option value="">{t.none}</option>
					<option value="true">{t.yes}</option>
					<option value="false">{t.no}</option>
				</select>
			)
		case 'SELECT':
			return (
				<select
					name={name}
					defaultValue={text}
					required={field.required}
					className="input mt-1"
				>
					<option value="">{t.none}</option>
					{field.options?.map((option) => (
						<option key={option}>{option}</option>
					))}
				</select>
			)
		case 'NUMBER':
			return (
				<input
					name={name}
					type="number"
					step="any"
					defaultValue={text}
					required={field.required}
					className="input mt-1"
				/>
			)
		case 'DATE':
			return (
				<input
					name={name}
					type="date"
					defaultValue={text}
					required={field.required}
					className="input mt-1"
				/>
			)
		default:
			return (
				<input
					name={name}
					defaultValue={text}
					required={field.required}
					className="input mt-1"
				/>
			)
	}
}

export default function DeviceForm({
	types,
	locations,
	device,
	error,
}: {
	types: DeviceType[]
	locations: Location[]
	device?: Device
	error?: string
}) {
	const t = useT()
	const usable = types.filter(
		(type) => !type.isArchived || type.id === device?.type.id
	)
	const [typeId, setTypeId] = useState(device?.type.id ?? usable[0]?.id)
	const type = usable.find((option) => option.id === Number(typeId))
	const busy = useNavigation().state !== 'idle'
	const label = 'block text-sm font-medium text-gray-700'

	return (
		<Form method="post" className="space-y-6">
			<section className="card grid gap-4 sm:grid-cols-2">
				<label className={label}>
					{t.form.type}
					<select
						name="typeId"
						value={typeId}
						onChange={(e) => setTypeId(Number(e.target.value))}
						disabled={!!device}
						className="input mt-1"
					>
						{usable.map((option) => (
							<option key={option.id} value={option.id}>
								{option.name} ({t.usage[option.usage]})
							</option>
						))}
					</select>
					{device && (
						<input type="hidden" name="typeId" value={device.type.id} />
					)}
				</label>
				{!device && (
					<label className={label}>
						{t.form.assetTag}
						<input
							name="assetTag"
							placeholder={type ? t.form.nextTag(type.tagPrefix) : ''}
							className="input mt-1"
						/>
						<span className="mt-1 block text-xs font-normal text-gray-500">
							{t.form.assetTagHelp}
						</span>
					</label>
				)}
				<label className={label}>
					{t.form.manufacturer}
					<input
						name="manufacturer"
						defaultValue={device?.manufacturer ?? ''}
						placeholder="HTC"
						className="input mt-1"
					/>
				</label>
				<label className={label}>
					{t.form.model}
					<input
						name="model"
						required
						defaultValue={device?.model ?? ''}
						placeholder="VIVE Focus 3"
						className="input mt-1"
					/>
				</label>
				<label className={label}>
					{t.form.name}
					<input
						name="name"
						defaultValue={device?.name ?? ''}
						placeholder={t.form.namePlaceholder}
						className="input mt-1"
					/>
				</label>
				<label className={label}>
					{t.form.serial}
					<input
						name="serialNumber"
						defaultValue={device?.serialNumber ?? ''}
						className="input mt-1"
					/>
				</label>
				<label className={label}>
					{t.form.location}
					<select
						name="locationId"
						defaultValue={device?.location?.id ?? ''}
						className="input mt-1"
					>
						<option value="">{t.none}</option>
						{locations
							.filter((l) => !l.isArchived || l.id === device?.location?.id)
							.map((l) => (
								<option key={l.id} value={l.id}>
									{l.name}
								</option>
							))}
					</select>
				</label>
				<label className={label}>
					{t.form.project}
					<input
						name="projectCode"
						defaultValue={device?.projectCode ?? ''}
						placeholder={t.form.projectPlaceholder}
						className="input mt-1 uppercase"
					/>
				</label>
				<label className={label}>
					{t.form.purchased}
					<input
						name="purchaseDate"
						type="date"
						defaultValue={device?.purchaseDate ?? ''}
						className="input mt-1"
					/>
				</label>
				<label className={label}>
					{t.form.warrantyEnds}
					<input
						name="warrantyEnd"
						type="date"
						defaultValue={device?.warrantyEnd ?? ''}
						className="input mt-1"
					/>
				</label>
			</section>

			{type && type.fields.length > 0 && (
				<section className="card grid gap-4 sm:grid-cols-2" key={type.id}>
					<h2 className="text-sm font-semibold text-gray-900 sm:col-span-2">
						{t.form.details(type.name)}
					</h2>
					{type.fields.map((field) => (
						<label key={field.key} className={label}>
							{field.label}
							{field.required && ' *'}
							{type.coverageField === field.key && (
								<span className="ml-1 text-xs font-normal text-gray-500">
									{t.form.coverageField}
								</span>
							)}
							<FieldInput field={field} value={device?.fields[field.key]} />
						</label>
					))}
				</section>
			)}

			<section className="card">
				<label className={label}>
					{t.form.notes}
					<textarea
						name="notes"
						rows={3}
						defaultValue={device?.notes ?? ''}
						className="input mt-1"
					/>
				</label>
			</section>

			{error && (
				<p
					role="alert"
					className="rounded-md bg-red-50 p-3 text-sm text-red-700"
				>
					{error}
				</p>
			)}
			<button type="submit" disabled={busy} className="btn-primary">
				{device ? t.form.save : t.form.add}
			</button>
		</Form>
	)
}

/** Reads the device form into the body the backend expects. */
export function readDeviceForm(form: FormData) {
	const text = (name: string) => {
		const value = String(form.get(name) ?? '').trim()
		return value === '' ? null : value
	}
	const fields: Record<string, string> = {}
	for (const [name, value] of form.entries()) {
		if (name.startsWith('field.')) fields[name.slice(6)] = String(value)
	}
	return {
		typeId: Number(form.get('typeId')),
		assetTag: text('assetTag'),
		manufacturer: text('manufacturer'),
		model: text('model') ?? '',
		name: text('name') ?? '',
		serialNumber: text('serialNumber'),
		locationId: text('locationId') ? Number(form.get('locationId')) : null,
		purchaseDate: text('purchaseDate'),
		warrantyEnd: text('warrantyEnd'),
		notes: text('notes'),
		projectCode: text('projectCode'),
		fields,
	}
}
