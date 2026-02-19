package com.secfix.todos.tenancy;

import java.util.UUID;

public class TenantContext {
    private static final ThreadLocal<UUID> currentTenant = new ThreadLocal<>();
    private static final ThreadLocal<Integer> currentUserId = new ThreadLocal<>();

    public static UUID getTenantId() {
        return currentTenant.get();
    }

    public static void setTenantId(UUID tenantId) {
        currentTenant.set(tenantId);
    }

    public static Integer getUserId() {
        return currentUserId.get();
    }

    public static void setUserId(Integer userId) {
        currentUserId.set(userId);
    }

    public static void clear() {
        currentTenant.remove();
        currentUserId.remove();
    }
}
