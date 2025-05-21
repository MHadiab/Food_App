package util;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * کلاس کمکی برای ارسال پاسخ‌های JSON
 */
public class JsonHelper {
    // یک نمونه‌ی ثابت از Gson برای serialize کردن اشیاء به JSON
    private static final Gson GSON = new Gson();

    /**
     * شیء body را به JSON تبدیل می‌کند و با وضعیت HTTP مشخص شده می‌فرستد.
     *
     * @param exchange  شیء HttpExchange مربوط به درخواست/پاسخ
     * @param statusCode  کد وضعیت HTTP (مثلاً 200، 201، 400، …)
     * @param body      شیء پاسخ (هر کلاس DTO یا Response) که به JSON تبدیل می‌شود
     * @throws IOException در صورت بروز خطا در نوشتن خروجی
     */
    public static void sendJson(HttpExchange exchange, int statusCode, Object body) throws IOException {
        // 1. تبدیل شیء به JSON (رشته‌ی UTF-8)
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);

        // 2. تنظیم هدر Content-Type
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");

        // 3. ارسال کد وضعیت و طول بدنه
        exchange.sendResponseHeaders(statusCode, bytes.length);

        // 4. نوشتن بدنه‌ی JSON در خروجی
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
