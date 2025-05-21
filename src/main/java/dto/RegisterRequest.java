package dto;

import entity.BankInfo;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {
    private String fullName;
    private String phone;
    private String email;
    private String password;
    private String role; // buyer|seller|courier
    private String address;
    private String profileImageBase64;
    private BankInfo bankInfo;
}