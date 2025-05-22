package response;

import lombok.Getter;
import lombok.Setter;

/**
 * پاسخِ ثبت‌نامِ کاربر
 */
@Getter
@Setter
public class RegisterResponse {

    private String message;
    private String user_id;
    private String token;

    public RegisterResponse(String message, String userId, String token) {
        this.message = message;
        this.user_id  = userId;
        this.token   = token;
    }
}
