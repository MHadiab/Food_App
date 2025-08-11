
[برای مشاهده نسخه فارسی اینجا کلیک کنید.](./READMEF.md)

---

# Advanced Programming Course Project: Online Food Ordering Backend

This project was developed as part of the requirements for the **Advanced Programming** course at the university. The main goal was to implement a RESTful API for an online food ordering platform, managing different roles such as buyer, seller, courier, and admin.

## 💻 Technologies Used

- **Programming Language:** Java
- **ORM (Object-Relational Mapping):** Hibernate
- **Database:** SQLite
- **API Documentation:** OpenAPI 3.0.3

## ✨ Core Features

This API provides comprehensive functionalities to manage a food ordering system:

- **Authentication & User Management (`auth`):**
  - Register, login, and logout for users with different roles (buyer, seller, courier).
  - View and edit user profiles.

- **Restaurant & Menu Management (`restaurant`):**
  - Create and edit restaurant information by sellers.
  - Add, edit, and delete food items from the menu.
  - Manage incoming orders for the restaurant.

- **Buyer Operations (`buyer`):**
  - Browse restaurants and food items with search functionality.
  - Place orders, view order history, and submit reviews for food.
  - Manage favorite restaurants.

- **Courier Operations (`courier`):**
  - View available orders ready for delivery.
  - Accept and manage the status of a delivery.

- **Admin Panel (`admin`):**
  - Full management of users, orders, and transactions.
  - Create and manage discount coupons.

## 🚀 How to Run

1.  **Clone the project:**
    ```bash
    git clone [YOUR_REPOSITORY_URL]
    ```

2.  **Install dependencies:**
    The project can be built using Maven or Gradle. Dependencies (like Hibernate and the SQLite driver) will be downloaded automatically.

3.  **Run the project:**
    After a successful build, run the application. The SQLite database file will be created automatically on the first run.

## 📄 API Documentation

Complete details for all endpoints, data models, and potential responses are defined in the `aut_food.yaml` file included in the project.
