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
        server.createContext("/auth/register", new HttpUserHandler());
        server.createContext("/auth/login",    new HttpUserHandler());
        server.createContext("/auth/profile",  new HttpUserHandler());
        server.createContext("/auth/logout",   new HttpUserHandler());
//
        server.createContext("/restaurants",      new HttpRestaurantHandler());
        server.createContext("/restaurants/mine", new HttpRestaurantHandler());
        server.createContext("/restaurants/",     new HttpRestaurantHandler());
//
        server.createContext("/deliveries",       new CourierHandler());
//        server.createContext("/vendors",          new BuyerHandler());
//        server.createContext("/cart",             new BuyerHandler());
//
        server.createContext("/orders",           new OrderHandler());
        server.createContext("/transactions",     new OrderHandler());
        server.createContext("/payment/online",   new OrderHandler());
        server.createContext("/wallet/top-up",   new OrderHandler());

//
        server.createContext("/admin/users",          new AdminHandler());
        server.createContext("/admin/users/",         new AdminHandler());
        server.createContext("/admin/orders",         new AdminHandler());
//        server.createContext("/admin/deliveries",     new AdminHandler());
        server.createContext("/admin/transactions",   new AdminHandler());
//        server.createContext("/admin/discounts",      new AdminHandler());
//        server.createContext("/admin/reports",        new AdminHandler());

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
