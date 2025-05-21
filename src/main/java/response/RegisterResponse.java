package response;

import lombok.Getter;
/**
 * پاسخِ ثبت‌نامِ کاربر
 */
@Getter
public class RegisterResponse {
    // پیامی که به کلاینت می‌گوییم ثبت‌نام با موفقیت انجام شد
    private String message;
    // شناسه‌ی رشته‌ای که در دیتابیس به کاربر تعلق گرفته
    private String userId;
    // توکن JWT که کاربر را برای درخواست‌های بعدی احراز هویت می‌کند
    private String token;

    /**
     * کانسترکتور اصلی برای برگشت دادن مقدارهای لازم
     * @param message پیام وضعیت
     * @param userId شناسه‌ی کاربر (String)
     * @param token توکن JWT
     */
    public RegisterResponse(String message, String userId, String token) {
        this.message = message;
        this.userId  = userId;
        this.token   = token;
    }
}
