package whatisMGC;

import io.github.cdimascio.dotenv.Dotenv;

public class DBConfig {
    private static final Dotenv dotenv = Dotenv.load();

    public static final String DB_HOST = dotenv.get("DB_HOST");
    public static final String DB_PORT = dotenv.get("DB_PORT");
    public static final String DB_NAME = dotenv.get("DB_NAME");
    public static final String DB_USER = dotenv.get("DB_USERNAME");
    public static final String DB_PASSWORD = dotenv.get("DB_PASSWORD"); //

    public static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME + "?useSSL=false&serverTimezone=UTC";



    private DBConfig() {}
}