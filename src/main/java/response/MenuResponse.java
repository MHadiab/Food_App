package response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MenuResponse {
    private String title;

    public MenuResponse(String title) {
        this.title = title;
    }
}
