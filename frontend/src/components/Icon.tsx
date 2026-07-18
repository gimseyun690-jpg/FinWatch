import type { ReactNode, SVGProps } from 'react'

export type IconName =
  | 'home'
  | 'search'
  | 'content'
  | 'disclosure'
  | 'star'
  | 'portfolio'
  | 'alert'
  | 'ai'
  | 'sync'
  | 'chevron-left'
  | 'chevron-right'
  | 'more'
  | 'close'
  | 'refresh'
  | 'external'
  | 'wifi-off'

type Props = Omit<SVGProps<SVGSVGElement>, 'name'> & {
  name: IconName
  size?: number
}

const paths: Record<IconName, ReactNode> = {
  home: <><path d="M3 11.5 12 4l9 7.5" /><path d="M5.5 10.5V20h13v-9.5M9.5 20v-6h5v6" /></>,
  search: <><circle cx="10.8" cy="10.8" r="6.8" /><path d="m16 16 4.5 4.5" /></>,
  content: <><path d="M5 4.5h14v15H5z" /><path d="M8 8h8M8 12h8M8 16h5" /></>,
  disclosure: <><path d="M6 3.5h9l3 3v14H6z" /><path d="M15 3.5v4h4M9 11h6M9 15h6M9 18h4" /></>,
  star: <path d="m12 3 2.7 5.5 6.1.9-4.4 4.3 1 6.1-5.4-2.9-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z" />,
  portfolio: <><path d="M3.5 7.5h17v12h-17z" /><path d="M8 7.5V5h8v2.5M3.5 11.5h17M10 11.5v2h4v-2" /></>,
  alert: <><path d="M6.5 9a5.5 5.5 0 0 1 11 0c0 6 2.2 6.2 2.2 7.7H4.3C4.3 15.2 6.5 15 6.5 9Z" /><path d="M9.5 19.5a2.7 2.7 0 0 0 5 0" /></>,
  ai: <><path d="M12 3.2 13.7 8l4.8 1.7-4.8 1.7-1.7 4.8-1.7-4.8-4.8-1.7L10.3 8 12 3.2Z" /><path d="m18.5 14 .8 2.2 2.2.8-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.8.8-2.2Z" /></>,
  sync: <><path d="M20 7v5h-5" /><path d="M4 17v-5h5" /><path d="M6.1 8.5A7 7 0 0 1 18.6 7L20 12M4 12l1.4 5a7 7 0 0 0 12.5-1.5" /></>,
  'chevron-left': <path d="m15 5-7 7 7 7" />,
  'chevron-right': <path d="m9 5 7 7-7 7" />,
  more: <><circle cx="5" cy="12" r="1" fill="currentColor" stroke="none" /><circle cx="12" cy="12" r="1" fill="currentColor" stroke="none" /><circle cx="19" cy="12" r="1" fill="currentColor" stroke="none" /></>,
  close: <path d="m5 5 14 14M19 5 5 19" />,
  refresh: <><path d="M20 7v5h-5" /><path d="M19 12a7 7 0 1 1-2-5" /></>,
  external: <><path d="M14 4h6v6M20 4l-9 9" /><path d="M19 13v6H5V5h6" /></>,
  'wifi-off': <><path d="m3 3 18 18" /><path d="M8.4 8.5A10.8 10.8 0 0 1 20 10.8M4 10.8a12 12 0 0 1 1.9-1.4M7.5 14.2a7.2 7.2 0 0 1 5.4-1.9M16.5 14.2l.7.6M10.5 17.6a2.6 2.6 0 0 1 3 0M12 21h.01" /></>,
}

export function Icon({ name, size = 20, ...props }: Props) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden={props['aria-label'] ? undefined : true}
      focusable="false"
      {...props}
    >
      {paths[name]}
    </svg>
  )
}
