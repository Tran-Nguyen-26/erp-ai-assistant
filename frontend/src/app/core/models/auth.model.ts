export type Role = 'ACCOUNTANT' | 'CHIEF_ACCOUNTANT' | 'ADMIN';

export interface UserResponse {
  id: string;
  username: string;
  fullName: string;
  role: Role;
  companyId: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserResponse | null;
}
