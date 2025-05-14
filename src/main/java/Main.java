//import HTTPhandler.UserHttpHandler;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;

public class Main {
    public static void main(String[] args) throws Exception {
        int port = 8090;
        System.out.println("WebSocket server started on port " + port);

        HttpServer server2 = HttpServer.create(new InetSocketAddress(8080), 0);

//        server2.createContext("/user", new UserHttpHandler());
//        server2.createContext("/chat", new ChatHttpHandler());
//        server2.createContext("/chatmember", new ChatMemberHttpHandler());
//        server2.createContext("/profile", new ProfileHttpHandler());
        server2.start();

        System.out.println("Server started on http://localhost:8080");
    }
}
