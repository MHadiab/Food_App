package dto;

import entity.BankInfo;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserInfo {
    private String id;
    private String full_name;
    private String phone;
    private String email;
    private String role;
    private String address;
    private String profileImageBase64;
    private BankInfo bank_info;
    public UserInfo(String id, String full_name,String phone,
                    String email,String role,String address,
                    String profileImageBase64,BankInfo bank_info) {
        this.id = id;
        this.full_name = full_name;
        this.phone = phone;
        this.email = email;
        this.role = role;
        this.address = address;
        this.profileImageBase64 = profileImageBase64;
        this.bank_info = bank_info;
    }
}
