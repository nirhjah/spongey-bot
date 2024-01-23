import com.fasterxml.jackson.core.JsonProcessingException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import org.bson.Document;
import org.bson.conversions.Bson;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;

public class CrownsCommands {

    static String connectionString = System.getenv("CONNECTION_STRING");

    static String claimCrown(Message message, GatewayDiscordClient client, MongoClient mongoClient, MongoDatabase mongoDatabase, String artist, String user, int ownerPlays) throws JsonProcessingException {
        MongoCollection<Document> crownsCollection = mongoDatabase.getCollection("crowns");
        Bson artistFilter = Filters.regex("artist", artist, "i");

        String crownClaimedString = "";

      /*  String username = client.getUserById(Snowflake.of(userId))
                .block()
                .getUsername();*/

        if (crownsCollection.countDocuments(artistFilter) == 0) {
            //create new crown object doc
            //dateClaimed is localdatetime
            Document crown = new Document("artist", artist)
                    .append("owner", user)
                    .append("dateClaimed", LocalDateTime.now())
                    .append("ownerPlays", ownerPlays);

            crownsCollection.insertOne(crown);

            crownClaimedString = user + " has claimed the crown with " + ownerPlays + " plays!";

        } else {
            //check existing crowns owner
            Document foundCrown = crownsCollection.find(artistFilter).first();
            if (Objects.equals(foundCrown.getString("owner"), user)) {
                //update owner plays
                Bson update = Updates.combine(
                        Updates.set("ownerPlays", ownerPlays)
                );

                crownsCollection.updateOne(artistFilter, update);

                crownClaimedString = "Current crown owner: " + user;

            } else {
                crownClaimedString = user + " has taken the crown from " + foundCrown.getString("owner") + " with " + ownerPlays + " plays!";


                Bson update = Updates.combine(
                        Updates.set("owner", user),
                        Updates.set("ownerPlays", ownerPlays),
                        Updates.set("dateClaimed", LocalDateTime.now())
                );

                crownsCollection.updateOne(artistFilter, update);
            }

        }





        return crownClaimedString;

    }



    static Mono<?> crownsCloseCommand(Message message, GatewayDiscordClient client) throws JsonProcessingException {
        long userid = message.getAuthor().get().getId().asLong();

        String username = client.getUserById(Snowflake.of(userid))
                .block()
                .getUsername();


        if (!message.getUserMentions().isEmpty()) {
            username = message.getUserMentions().get(0).getUsername();
            userid = message.getUserMentions().get(0).getId().asLong();
        }

        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed("Crowns " + username + " is close to getting");

        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> crowns = database.getCollection("crowns");
        Map<String, Integer> crownsClose = new HashMap<>(); //artist, scrobbles away from crown

        for (Document crown : crowns.find()) {
            int authorsArtistPlays = Service.getArtistPlays(userid, crown.getString("artist"), client);

            double result = Math.cbrt(Math.pow(crown.getInteger("ownerPlays"), 2) * 0.75);
            int roundedResult = (int) Math.ceil(result);

            if ( (!Objects.equals(crown.getString("owner"), username)) && ((crown.getInteger("ownerPlays") - authorsArtistPlays) <= roundedResult) && (authorsArtistPlays != 0 ))  {
                System.out.println("Thi is the calculation: " + roundedResult);
            //if (((crown.getInteger("ownerPlays")00 - authorsArtistPlays) <= 20)  && (crown.getInteger("ownerPlays") - authorsArtistPlays) != 0 && (!Objects.equals(crown.getString("owner"), username)) ) {
                crownsClose.put(crown.getString("artist"), crown.getInteger("ownerPlays") - authorsArtistPlays);
                System.out.println("This is owner plays: " + crown.getInteger("ownerPlays"));
                System.out.println("This is the person who entered the cmoomands' plays: " + authorsArtistPlays);

            }


        }

        List<Map.Entry<String, Integer>> crownsCloseList = new ArrayList<>(crownsClose.entrySet());
        Collections.sort(crownsCloseList, Map.Entry.<String, Integer>comparingByValue().reversed());

        StringBuilder crownsString = new StringBuilder();
        for (Map.Entry<String, Integer> entry : crownsCloseList) {
            crownsString.append(entry.getKey()).append(": ").append(entry.getValue()).append(" scrobbles away \n");
        }

        embedBuilder.addField("", crownsString.toString(), false);



        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));

    }

    static Mono<?> getCrowns(Message message, GatewayDiscordClient client) throws JsonProcessingException {
        long userid = message.getAuthor().get().getId().asLong();

        String username = client.getUserById(Snowflake.of(userid))
                .block()
                .getUsername();


        if (!message.getUserMentions().isEmpty()) {
            username = message.getUserMentions().get(0).getUsername();
            userid = message.getUserMentions().get(0).getId().asLong();
        }


        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> crowns = database.getCollection("crowns");
        Bson filter = Filters.all("owner", username);

        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed(username + "'s crowns");
        Map<String, Integer> crownMap = new HashMap<>(); //owner, total crowns

        int totalCrowns = 0;
        for (Document crown : crowns.find(filter)) {
            totalCrowns += 1;
            crownMap.put(crown.getString("artist"), crown.getInteger("ownerPlays"));
        }

        List<Map.Entry<String, Integer>> listOfCrowns = new ArrayList<>(crownMap.entrySet());
        Collections.sort(listOfCrowns, Map.Entry.<String, Integer>comparingByValue().reversed());

        int count = 1;
        StringBuilder crownsString = new StringBuilder();
        for (Map.Entry<String, Integer> entry : listOfCrowns) {
            crownsString.append(count).append(". ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" scrobbles \n");
            count++;
        }

        embedBuilder.addField("", crownsString.toString(), false);

        embedBuilder.footer(totalCrowns + " total crowns", "https://i.imgur.com/F9BhEoz.png");

        embedBuilder.thumbnail(client.getUserById(Snowflake.of(userid))
                .block()
                .getAvatarUrl());

        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));


    }



}
