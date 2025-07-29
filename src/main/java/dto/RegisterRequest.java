package dto;

import com.google.gson.annotations.SerializedName;
import entity.BankInfo;
import entity.Role;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {
    private String full_name;
    private String phone;
    private String email;
    private String password;
    private String role;
    private String address;
    @SerializedName("profileImageBase64")
    private String profileImageBase64;
    private BankInfo bank_info;
}