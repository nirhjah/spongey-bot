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

    static Mono<?> testUsername(GatewayDiscordClient client) {
        MongoClient mongoClient = MongoClients.create(connectionString);
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


    static Mono<?> testingbetterupdatecommand() {

        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> userScrobblesCollection = database.getCollection("viperfan");




        long totalDocuments = userScrobblesCollection.countDocuments();
        System.out.println("Total number of documents: " + totalDocuments);


        Document documentWithMaxTimestamp = null;
        int maxTimestamp = 0;

        List<Scrobble> scrobls = new ArrayList<>();

        FindIterable<Document> documents = userScrobblesCollection.find();
        for (Document document : documents) {
            // Get the timestamp from the current document
            int timestamp = document.getInteger("timestamp");

            // Check if the current timestamp is greater than the maximum
            if (timestamp > maxTimestamp) {
                maxTimestamp = timestamp;
                documentWithMaxTimestamp = document;
            }

            Scrobble scrobble = Scrobble.fromDocument(document);
            scrobls.add(scrobble);
        }

        System.out.println(" scrobble size: " + scrobls.size());

        Collections.sort(scrobls, Comparator.comparingLong(Scrobble::getTimestamp));


        System.out.println("Max timestamp: " + maxTimestamp);
        System.out.println(documentWithMaxTimestamp);


        int size = scrobls.size();

        int startIndex = Math.max(0, size - 10);  // Ensure startIndex is not negative

        List<Scrobble> lastTenScrobbles = scrobls.subList(startIndex, size);

// Print the last 5 scrobbles
        for (Scrobble scrobble : lastTenScrobbles) {
            System.out.println(scrobble);
        }


        return Mono.empty();

    }


}
