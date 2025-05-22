package dto;

import entity.BankInfo;
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
    private String profileImageBase64;
    private BankInfo bank_info;
}