import com.fasterxml.jackson.core.JsonProcessingException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.Embed;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateFields;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import org.bson.Document;
import org.bson.conversions.Bson;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;

public class CrownsCommands {
    static String connectionString = System.getenv("CONNECTION_STRING");


    private static final MongoClient mongoClient = MongoClients.create(connectionString);


    private static int currentPage = 1;


    static String claimCrown(MongoDatabase mongoDatabase, String artist, String user, int ownerPlays) {
        MongoCollection<Document> crownsCollection = mongoDatabase.getCollection("crowns");
        Bson artistFilter = Filters.regex("artist", artist, "i");

        String crownClaimedString = "";


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


    static void handleCrownsButtonClick(Message message, Embed oldEmbed, boolean next) {

        if (next) {
            currentPage++;
        } else {
            currentPage--;
        }


        EmbedCreateSpec.Builder embedBuilder = EmbedCreateSpec.builder();
        Embed.Author existingAuthor = oldEmbed.getAuthor().orElse(null);
        String authorName = existingAuthor != null ? existingAuthor.getName().orElse(null) : null;
        String authorUrl = existingAuthor != null ? existingAuthor.getUrl().orElse(null) : null;
        String authorIconUrl = existingAuthor != null ? existingAuthor.getIconUrl().orElse(null) : null;

        Embed.Footer existingFooter = oldEmbed.getFooter().get();
        String footerText = existingFooter.getText();
        String footerURL = existingFooter.getIconUrl().get();

        embedBuilder.color(oldEmbed.getColor().get())
                .author(EmbedCreateFields.Author.of(authorName, authorUrl, authorIconUrl)).footer(footerText, footerURL);



        ActionRow editableActionRow = (ActionRow) message.getComponents().get(0);
        Button editablePrev = (Button) editableActionRow.getChildren().get(0);
        Button editableNext = (Button) editableActionRow.getChildren().get(1);

        String[] title = authorName.split(" ");

        String username = title[0];



        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> allCrowns = database.getCollection("crowns");
        Bson filter = Filters.all("owner", username);

        Map<String, Integer> crownMap = Service.getUsersCrowns(allCrowns, filter);

        ArrayList<Map.Entry<String, Integer>> crowns = new ArrayList<>(crownMap.entrySet());


        Collections.sort(crowns, Map.Entry.<String, Integer>comparingByValue().reversed());



        int startIndex = (currentPage - 1) * 10;
        int endIndex = Math.min(startIndex + 10, crowns.size());


        System.out.println("This is start index: " + startIndex);
        System.out.println("This is end index: " + endIndex);

        if (startIndex > endIndex) {
            System.out.println("index too big");
            message.edit().withEmbeds(embedBuilder.build()).subscribe();
        }

        List<Map.Entry<String, Integer>> currentCrowns = crowns.subList(startIndex, endIndex);


        StringBuilder fieldToSave = new StringBuilder();


        int counter = startIndex;
        for (Map.Entry<String, Integer> crown : currentCrowns) {
            fieldToSave.append(counter).append(". ").append(crown.getKey()).append(": ").append(crown.getValue()).append(" scrobbles \n");
            counter += 1;
        }


        System.out.println("This is counter size: " + counter + " and this is list size: " + crowns.size());
        if (startIndex == 0) {
            System.out.println("start index is 0");
            editablePrev = editablePrev.disabled(true);
            editableNext = editableNext.disabled(false);
        } else if (counter == crowns.size()) {
            System.out.println("WE are displaying the last item on the list currently.");
            editableNext = editableNext.disabled(true);
            editablePrev = editablePrev.disabled(false);
        } else if (counter < crowns.size()) {
            System.out.println("counter smaller than tracksnotlistened size");
            editableNext = editableNext.disabled(false);
            editablePrev = editablePrev.disabled(false);
        }
        else {
            System.out.println("in the else");
            editablePrev = editablePrev.disabled(false);
        }


        embedBuilder.addField("", fieldToSave.toString(), false);

        ActionRow newActionRow = ActionRow.of(editablePrev, editableNext);
        message.edit().withEmbeds(embedBuilder.build()).withComponents(newActionRow).subscribe();


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

            currentPage = 1;


            MongoDatabase database = mongoClient.getDatabase("spongeybot");
            MongoCollection<Document> crowns = database.getCollection("crowns");
            Bson filter = Filters.all("owner", username);

            EmbedCreateSpec.Builder embedBuilder = Service.createEmbed(username + " crowns");
            Map<String, Integer> crownMap; //owner, total crowns


            crownMap = Service.getUsersCrowns(crowns, filter);


        List<Map.Entry<String, Integer>> listOfCrowns = new ArrayList<>(crownMap.entrySet());
        Collections.sort(listOfCrowns, Map.Entry.<String, Integer>comparingByValue().reversed());

        System.out.println("List of crowns: " + listOfCrowns);

        int count = 1;
        StringBuilder crownsString = new StringBuilder();
        for (Map.Entry<String, Integer> entry : listOfCrowns) {
            crownsString.append(count).append(". ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" scrobbles \n");
            count++;
        }

       // embedBuilder.addField("", crownsString.toString(), false);




            if (currentPage == 1) {
                int endIndex = Math.min(10, listOfCrowns.size());
                List<Map.Entry<String, Integer>> currentCrowns = listOfCrowns.subList(0, endIndex);


                StringBuilder fieldToSave = new StringBuilder();


                int counter = 1;
                for (Map.Entry<String, Integer> crown : currentCrowns) {
                    fieldToSave.append(counter).append(". ").append(crown.getKey()).append(": ").append(crown.getValue()).append(" scrobbles \n");
                    counter += 1;
                }

                embedBuilder.addField("", fieldToSave.toString(), false);

            }






        embedBuilder.footer(crownMap.size() + " total crowns", "https://i.imgur.com/F9BhEoz.png");

        embedBuilder.thumbnail(client.getUserById(Snowflake.of(userid))
                .block()
                .getAvatarUrl());

        Button nextButton = Button.secondary("nextCrowns", "Next Page");
        Button prevButton = Button.secondary("prevCrowns", "Prev Page").disabled();


            if (listOfCrowns.size() < 10) {
                nextButton = nextButton.disabled(true);
            }

        ActionRow actionRow = ActionRow.of(prevButton, nextButton);


        MessageCreateSpec messageCreateSpec = MessageCreateSpec.builder()
                    .addEmbed(embedBuilder.build())
                    .addComponent(actionRow)
                    .build();

        return message.getChannel().flatMap(channel -> channel.createMessage(messageCreateSpec));



    }



}
