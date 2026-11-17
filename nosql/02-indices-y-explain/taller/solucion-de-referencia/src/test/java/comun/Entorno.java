package comun;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

/** Direcciones del entorno de los talleres (`nosql/entorno`, con los puertos publicados por defecto). */
public final class Entorno {
    private Entorno() {}

    public static String mongoUrl() {
        return System.getenv().getOrDefault("MONGO_URL", "mongodb://localhost:27017/?replicaSet=rs0");
    }

    public static String redisUrl() {
        return System.getenv().getOrDefault("REDIS_URL", "redis://localhost:6379");
    }

    public static MongoClient mongo() {
        return MongoClients.create(mongoUrl());
    }
}
