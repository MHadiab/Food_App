//package util;
//
//import org.mindrot.jbcrypt;
//
//public class PasswordUtil {
//    /**
//     * تولید هش از پسورد کلیر تکست
//     * @param plainPassword پسورد خام
//     * @return رشته‌ی هش‌شده
//     */
//    public static String hashPassword(String plainPassword) {
//        // عدد work factor (log rounds) بین 10 تا 12 معمول است
//        int workload = 12;
//        String salt = BCrypt.gensalt(workload);
//        return BCrypt.hashpw(plainPassword, salt);
//    }
//    /**
//     * مقایسهٔ پسورد خام با هش ذخیره‌شده
//     * @param plainPassword پسورد واردشده
//     * @param hashedPassword هش ذخیره‌شده در دیتابیس
//     * @return true اگر مطابقت داشت
//     */
//    public static boolean checkPassword(String plainPassword, String hashedPassword) {
//        if (hashedPassword == null || !hashedPassword.startsWith("$2a$")) {
//            throw new IllegalArgumentException("Invalid hash provided for comparison");
//        }
//        return BCrypt.checkpw(plainPassword, hashedPassword);
//    }
//}
