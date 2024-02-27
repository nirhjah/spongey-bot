import com.mongodb.client.*;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import org.bson.Document;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public class AdminCommands {

    static String connectionString = System.getenv("CONNECTION_STRING");

    private static final MongoClient mongoClient = MongoClients.create(connectionString);

    static Mono<?> testUsername(GatewayDiscordClient client) {
        MongoDatabase database = mongoClient.getDatabase("spongeybot");

        MongoCollection<Document> users = database.getCollection("users");


        for (Document document : users.find()) {
            long userId = document.getLong("userid");


            String username = client.getUserById(Snowflake.of(userId))
                    .block()
                    .getUsername();


            System.out.println("this is user: " + userId + " username: " + username);

            MongoCollection<Document> userScrobblesCollection = database.getCollection(username);
            System.out.println("total scrobbles: " + userScrobblesCollection.countDocuments());


        }


        return Mono.empty();

    }



}
