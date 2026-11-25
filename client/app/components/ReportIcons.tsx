// Line icons for the report, drawn like ProDash's (24px grid, 2px stroke, currentColor)
const base = {
	viewBox: '0 0 24 24',
	fill: 'none',
	stroke: 'currentColor',
	strokeWidth: 2,
	strokeLinecap: 'round' as const,
	strokeLinejoin: 'round' as const,
	'aria-hidden': true,
}

export const Icon = {
	box: () => (
		<svg {...base}>
			<path d="M21 8l-9-5-9 5 9 5 9-5z" />
			<path d="M3 8v8l9 5 9-5V8" />
			<path d="M12 13v8" />
		</svg>
	),
	handoff: () => (
		<svg {...base}>
			<path d="M7 11l3-3a2 2 0 013 0l1 1" />
			<path d="M3 13l4-4" />
			<path d="M21 11l-4 4-3-1-3 3-4-4" />
			<path d="M14 9l3 3 4-4" />
		</svg>
	),
	alert: () => (
		<svg {...base}>
			<circle cx="12" cy="12" r="9" />
			<path d="M12 7v6" />
			<path d="M12 16.5v.5" />
		</svg>
	),
	warning: () => (
		<svg {...base}>
			<path d="M10.3 3.9L2.4 18a2 2 0 001.7 3h15.8a2 2 0 001.7-3L13.7 3.9a2 2 0 00-3.4 0z" />
			<path d="M12 9v4" />
			<path d="M12 17v.5" />
		</svg>
	),
	check: () => (
		<svg {...base}>
			<circle cx="12" cy="12" r="9" />
			<path d="M8 12l3 3 5-6" />
		</svg>
	),
	gauge: () => (
		<svg {...base}>
			<path d="M4 18a8 8 0 1116 0" />
			<path d="M12 18l4-6" />
		</svg>
	),
	grid: () => (
		<svg {...base}>
			<rect x="3" y="3" width="7" height="7" rx="1.5" />
			<rect x="14" y="3" width="7" height="7" rx="1.5" />
			<rect x="3" y="14" width="7" height="7" rx="1.5" />
			<rect x="14" y="14" width="7" height="7" rx="1.5" />
		</svg>
	),
	hourglass: () => (
		<svg {...base}>
			<path d="M6 3h12" />
			<path d="M6 21h12" />
			<path d="M7 3c0 5 10 5 10 9s-10 4-10 9" />
			<path d="M17 3c0 5-10 5-10 9s10 4 10 9" />
		</svg>
	),
	clock: () => (
		<svg {...base}>
			<circle cx="12" cy="12" r="9" />
			<path d="M12 7v5l3 2" />
		</svg>
	),
	layers: () => (
		<svg {...base}>
			<path d="M12 3l9 5-9 5-9-5 9-5z" />
			<path d="M3 13l9 5 9-5" />
		</svg>
	),
	users: () => (
		<svg {...base}>
			<circle cx="9" cy="8" r="3.5" />
			<path d="M2.5 20a6.5 6.5 0 0113 0" />
			<path d="M16 4.5a3.5 3.5 0 010 7" />
			<path d="M18 14a6.5 6.5 0 013.5 6" />
		</svg>
	),
	image: () => (
		<svg {...base}>
			<rect x="3" y="4" width="18" height="16" rx="2" />
			<circle cx="9" cy="10" r="2" />
			<path d="M21 17l-5-5-9 8" />
		</svg>
	),
	download: () => (
		<svg {...base}>
			<path d="M12 4v11" />
			<path d="M7 10l5 5 5-5" />
			<path d="M5 20h14" />
		</svg>
	),
}
