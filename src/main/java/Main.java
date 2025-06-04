import HTTPhandler.*;
import com.sun.net.httpserver.HttpServer;
import entity.Role;
import entity.User;
import org.hibernate.Session;
import org.hibernate.Transaction;
import util.HibernateUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
public class Main {
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/auth", new HttpUserHandler());
        server.createContext("/restaurants",      new RestaurantDispatcher());
        server.createContext("/orders",           new BuyerHandler());
        server.createContext("/transactions",     new OrderHandler());
        server.createContext("/payment/online",   new OrderHandler());
        server.createContext("/wallet/top-up",   new OrderHandler());
        server.createContext("/admin",          new AdminHandler());
        server.createContext("/favorites",     new BuyerHandler());
        server.createContext("/deliveries", new CourierHandler());

        server.start();
        System.out.println("Server listening on 8080");
                HibernateUtil.getSessionFactory().openSession().close();
                System.out.println("Tables should be created if not exist.");

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            Long count = session.createQuery(
                            "select count(u) from User u where u.role = :adminRole", Long.class)
                    .setParameter("adminRole", Role.ADMIN)
                    .uniqueResult();
            if (count == 0) {
                User admin = new User();
                admin.setFull_name("admin");
                admin.setPhone("admin");
                admin.setEmail("admin@example.com");
                admin.setPassword("admin");
                admin.setRole(Role.ADMIN);
                admin.setAddress("HQ");
                session.persist(admin);
            }
            tx.commit();
        }

    }
}
