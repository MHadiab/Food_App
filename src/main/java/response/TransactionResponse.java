package response;
import entity.Transaction;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransactionResponse {
    private String id;
    private String type;
    private String amount;
    private String date;
    public TransactionResponse(Transaction tx) {
        this.id     = tx.getId().toString();
        this.type   = tx.getType().name().toLowerCase();
        this.amount = String.valueOf(tx.getAmount());
        this.date   = tx.getDate().toString();
    }
}
