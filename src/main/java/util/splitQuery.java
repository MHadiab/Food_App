package util;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

public class splitQuery {
    public static Map<String,String> splitQuery(String query) {
        if (query == null || query.isEmpty()) {
            return Collections.emptyMap();
        }
        return Arrays.stream(query.split("&"))
                .map(s -> s.split("=", 2))
                .filter(arr -> arr.length == 2)
                .collect(Collectors.toMap(
                        arr -> URLDecoder.decode(arr[0], StandardCharsets.UTF_8),
                        arr -> URLDecoder.decode(arr[1], StandardCharsets.UTF_8)
                ));
    }

}
