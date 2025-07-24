package dto;

import entity.BankInfo;
import entity.User;
import entity.UserStatus;
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
    private UserStatus status;
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

    public UserInfo(User user) {
        this.id=user.getUser_id().toString();
        this.full_name=user.getFull_name();
        this.phone=user.getPhone();
        this.email=user.getEmail();
        this.role=user.getRole().toString();
        this.address=user.getAddress();
        this.profileImageBase64=user.getProfileImageBase64();
        this.bank_info=user.getBank_info();
        this.status=user.getStatus();
    }
}
