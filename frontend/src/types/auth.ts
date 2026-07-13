import type { ApiResponse } from './stock'

export type { ApiResponse }

export type UserRole = 'USER' | 'ADMIN'

export type AuthUser = {
  id: number
  email: string
  role: UserRole
}

export type LoginResponse = {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: AuthUser
}

export type AuthSession = LoginResponse & {
  expiresAt: number
}
