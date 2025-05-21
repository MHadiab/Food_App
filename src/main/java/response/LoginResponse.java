package response;

import dto.UserInfo;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginResponse {
    private String message;
    private String token;
    private UserInfo user;
    public LoginResponse(String message, String token,UserInfo user) {
        this.message = message;
        this.token = token;
        this.user = user;
    }
}