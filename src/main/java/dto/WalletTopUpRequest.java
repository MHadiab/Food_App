package dto;

import entity.Method;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WalletTopUpRequest {
    private Long amount;
}
