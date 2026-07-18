import type { ApiResponse } from './stock'

export type { ApiResponse }

export type UserRole = 'USER' | 'ADMIN'

export type AuthUser = {
  id: number
  displayName: string
  email: string | null
  profileImageUrl: string | null
  role: UserRole
  authProvider: 'LOCAL' | 'KAKAO'
}

export type LoginResponse = {
  authenticated: true
  expiresAt: string
  user: AuthUser
}

export type AuthSession = LoginResponse

export type KakaoLoginStatus = {
  enabled: boolean
}
