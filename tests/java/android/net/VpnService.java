package android.net;

import java.util.ArrayList;
import java.util.List;

public class VpnService {
    public static class Builder {
        public final List<String> routes = new ArrayList<>();
        public Builder addRoute(String address, int prefix) {
            routes.add(address + "/" + prefix);
            return this;
        }
    }
}
