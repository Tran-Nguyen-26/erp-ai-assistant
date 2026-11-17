package vn.edu.uet.erpai.common.security;

import java.util.UUID;

/** Principal đã xác thực, lấy từ JWT. */
public record CurrentUser(UUID id, String username, Role role, String companyId) {}
