package response;
import entity.Transaction;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransactionResponse {
    private Integer id;
    private Integer order_id;
    private Integer user_id;
    private String method;
    private String status;
    public TransactionResponse(Transaction tx) {
        this.id= Math.toIntExact(tx.getId());
        if(tx.getOrder() != null) this.order_id = Math.toIntExact(tx.getOrder().getId());
        else this.order_id = 0;
        this.user_id= Math.toIntExact(tx.getUser().getUser_id());
        this.method=tx.getMethod().toString();
        this.status=tx.getStatus().toString();
    }
}
