import com.fasterxml.jackson.core.JsonProcessingException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
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

import java.io.UnsupportedEncodingException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class UserScrobbleCommands {


    static String connectionString = System.getenv("CONNECTION_STRING");

    static StringBuilder getArtistTrackOrAlbumInfoForYear(String year, GatewayDiscordClient client, Message message, String username) {

        StringBuilder fieldToSave = new StringBuilder();

        int currentTimeUTS = (int) (System.currentTimeMillis() / 1000);


        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection(username);


        long startOfGivenYearUTS;
        long endOfGivenYearUTS;

        LocalDateTime givenStartDateTime = LocalDateTime.of(Integer.parseInt(year), Month.JANUARY, 1, 0, 0);
        LocalDateTime givenEndDateTime = LocalDateTime.of(Integer.parseInt(year), Month.DECEMBER, 31, 23, 59);

        if (year.equals("")) {
            startOfGivenYearUTS = 1704020400;
            endOfGivenYearUTS = currentTimeUTS;
        } else {
            startOfGivenYearUTS = Service.convertToUnixTimestamp(givenStartDateTime);
            endOfGivenYearUTS = Service.convertToUnixTimestamp(givenEndDateTime);
        }

        int count = 1;
        Map<String, Integer> artistsForYear = new HashMap<>();
        Map<List<String>, Integer> tracksForYearNew = new HashMap<>();
        Map<List<String>, Integer> albumsForYearNew = new HashMap<>();
        if (currentPage == 1) {

            for (Document doc : collection.find()) {

                int timestamp = doc.getInteger("timestamp");

                if (timestamp  < endOfGivenYearUTS && timestamp >= startOfGivenYearUTS) {
                   // scrobbleCounterForYear += 1;
                    String artistName = doc.getString("artist") ;
                    int currentCount = artistsForYear.getOrDefault(artistName, 0);
                    artistsForYear.put(artistName, currentCount + 1);

                }
            }


            List<Map.Entry<String, Integer>> entryListArtist = new ArrayList<>(artistsForYear.entrySet());
            entryListArtist.sort(Map.Entry.<String, Integer>comparingByValue().reversed());


            for (Map.Entry<String, Integer> entry : entryListArtist) {

                if (count == 1) {
                    fieldToSave.append("\uD83E\uDD47 **").append(entry.getKey()).append("** - ").append(entry.getValue()).append("\n");
                } else if (count == 2) {
                    fieldToSave.append("\uD83E\uDD48 **").append(entry.getKey()).append("** - ").append(entry.getValue()).append("\n");
                } else if (count == 3) {
                    fieldToSave.append("\uD83E\uDD49 **").append(entry.getKey()).append("** - ").append(entry.getValue()).append("\n");
                }

                else {
                    fieldToSave.append(count).append(". **").append(entry.getKey()).append("** - ").append(entry.getValue()).append("\n");
                }

                count++;
                if (count > 10) {
                    break;
                }
            }


        }

        else if (currentPage == 2) {

            for (Document doc : collection.find()) {


                int timestamp = doc.getInteger("timestamp");

                if (timestamp  < endOfGivenYearUTS && timestamp >= startOfGivenYearUTS) {
                    //scrobbleCounterForYear += 1;
                    String artistName = doc.getString("artist") ;

                    String trackName = doc.getString("track");
                    List<String> trackArtistPair = new ArrayList<>();
                    trackArtistPair.add(trackName);
                    trackArtistPair.add(artistName);
                    int currentCountTrack = tracksForYearNew.getOrDefault(trackArtistPair, 0);
                    tracksForYearNew.put(trackArtistPair, currentCountTrack + 1);



                }
            }

            List<Map.Entry<List<String>, Integer>> entryListTracks = new ArrayList<>(tracksForYearNew.entrySet());
            entryListTracks.sort(Map.Entry.<List<String>, Integer>comparingByValue().reversed());



            for (Map.Entry<List<String>, Integer> entry : entryListTracks) {
                if (count == 1) {
                    fieldToSave.append("\uD83E\uDD47 **").append(entry.getKey().get(0)).append("** - ").append(entry.getValue()).append("\n");
                }
                else if (count == 2) {
                    fieldToSave.append("\uD83E\uDD48 **").append(entry.getKey().get(0)).append("** - ").append(entry.getValue()).append("\n");
                }
                else if (count == 3) {
                    fieldToSave.append("\uD83E\uDD49 **").append(entry.getKey().get(0)).append("** - ").append(entry.getValue()).append("\n");
                }

                else {
                    fieldToSave.append(count).append(". **").append(entry.getKey().get(0)).append("** - ").append(entry.getValue()).append("\n");

                }


                count++;
                if (count > 10) {
                    break;
                }
            }


        } else if (currentPage == 3) {

            for (Document doc : collection.find()) {

                int timestamp = doc.getInteger("timestamp");

                if (timestamp  < endOfGivenYearUTS && timestamp >= startOfGivenYearUTS) {
                  //  scrobbleCounterForYear += 1;
                    String artistName = doc.getString("artist") ;


                    String albumName = doc.getString("album");
                    List<String> albumArtistPair = new ArrayList<>();
                    albumArtistPair.add(albumName);
                    albumArtistPair.add(artistName);
                    int currentCountAlbum = albumsForYearNew.getOrDefault(albumArtistPair, 0);
                    albumsForYearNew.put(albumArtistPair, currentCountAlbum + 1);

                }
            }


            List<Map.Entry<List<String>, Integer>> entryListAlbums = new ArrayList<>(albumsForYearNew.entrySet());
            entryListAlbums.sort(Map.Entry.<List<String>, Integer>comparingByValue().reversed());



            for (Map.Entry<List<String>, Integer> entry : entryListAlbums) {

                if (count == 1) {
                    fieldToSave.append("\uD83E\uDD47 **").append(entry.getKey().get(0)).append("**: ").append(entry.getValue()).append("\n");


                }

                else if (count == 2) {
                    fieldToSave.append("\uD83E\uDD48 **").append(entry.getKey().get(0)).append("** - ").append(entry.getValue()).append("\n");


                }

                else if (count == 3) {
                    fieldToSave.append("\uD83E\uDD49 **").append(entry.getKey().get(0)).append("** - ").append(entry.getValue()).append("\n");


                }
                else {
                    fieldToSave.append(count).append(". ").append(entry.getKey().get(0)).append(" - ").append(entry.getValue()).append("\n");
                }

                count++;
                if (count > 10) {
                    break;
                }
            }



        }


        return fieldToSave;

    }




    static Mono<?> getYearlyInfo(Message message, GatewayDiscordClient client) throws JsonProcessingException {

        String[] command = message.getContent().split(" ");

        String year = "2024";
        if (command.length >= 2) {
            year = command[1];
            System.out.println("Year: " + year);
        } else {
            return message.getChannel().flatMap(channel -> channel.createMessage("Valid commands: $year 2023, $year 2022, $year 2021 etc"));

        }


        String username2 = client.getUserById(Snowflake.of(message.getAuthor().get().getId().asLong()))
                .block()
                .getUsername();


        if (!message.getUserMentions().isEmpty()) {
            username2 = message.getUserMentions().get(0).getUsername();
        }

        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed(username2 + " stats for " + year);


        //embedBuilder.addField("Total scrobbles: ", String.valueOf(scrobbleCounterForYear), false);


        if (currentPage == 1) {
            embedBuilder.addField("Top 10 Artists", getArtistTrackOrAlbumInfoForYear(year, client, message, username2).toString(), false);
        }



        Button nextButton = Button.secondary("next", "Next Page");
        Button prevButton = Button.secondary("prev", "Prev Page");



        ActionRow actionRow = ActionRow.of(nextButton, prevButton);


        MessageCreateSpec messageCreateSpec = MessageCreateSpec.builder()
                .addEmbed(embedBuilder.build())
                .addComponent(actionRow)
                .build();



        return message.getChannel().flatMap(channel -> channel.createMessage(messageCreateSpec));
    }

    private static int currentPage = 1;

    private static void handleButtonClick(Message message, Embed oldEmbed, GatewayDiscordClient client, boolean next) {

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

        String totalScrobbles = oldEmbed.getFields().stream()
                .filter(field -> "Total scrobbles:".equals(field.getName()))
                .findFirst()
                .map(Embed.Field::getValue)
                .orElse(null);

        String[] words = authorName.split("\\s+");
        String year = words[words.length - 1];
        String username = words[0];


        embedBuilder.color(oldEmbed.getColor().get())
                .author(EmbedCreateFields.Author.of(authorName, authorUrl, authorIconUrl));
             //   .addField("Total scrobbles: ", totalScrobbles, false);


        if (currentPage == 1) {
            System.out.println("On page 1 so: artists");
             embedBuilder.addField("Top 10 Artists", getArtistTrackOrAlbumInfoForYear(year, client, message, username).toString(), false);
        }
        if (currentPage == 2) {
            System.out.println("On page 2 so: tracks");
            embedBuilder.addField("Top 10 Tracks", getArtistTrackOrAlbumInfoForYear(year, client, message, username).toString(), false);

        } if (currentPage == 3) {
            System.out.println("On page 3 so: albums");
            embedBuilder.addField("Top 10 Albums", getArtistTrackOrAlbumInfoForYear(year, client, message, username).toString(), false);
        }

        message.edit().withEmbeds(embedBuilder.build()).subscribe();
    }

    public static Mono<Void> handleButtonInteraction(ButtonInteractionEvent event, GatewayDiscordClient client) {
        System.out.println("handling button interaction");
        String customId = event.getCustomId();
        Message message = event.getMessage().orElse(null);

        if ("next".equals(customId)) {
            handleButtonClick(message, message.getEmbeds().get(0), client, true);
        } else if ("prev".equals(customId)) {
            handleButtonClick(message, message.getEmbeds().get(0), client, false);
        }

        return Mono.empty();
    }


        static Mono<?> getScrobblesInTimeframe(Message message, GatewayDiscordClient client) throws JsonProcessingException {

        String[] command = message.getContent().split(" ");

        String year = "";
        if (command.length >= 2) {
            year = command[1];
            System.out.println("Year: " + year);
        } else {
            return message.getChannel().flatMap(channel -> channel.createMessage("Valid commands: $scrobbles 2023, $scrobbles 2022, $scrobbles 2021 etc"));

        }

        int scrobbleCounterForYear = 0;
        int currentTimeUTS = (int) (System.currentTimeMillis() / 1000);

        String username = client.getUserById(Snowflake.of(message.getAuthor().get().getId().asLong()))
                .block()
                .getUsername();

        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection(username);

        //gets all docs in scrobbles collection for a user
        Bson filter = Filters.all("userId", message.getAuthor().get().getId().asLong());

        long startOfGivenYearUTS;
        long endOfGivenYearUTS;

        LocalDateTime givenStartDateTime = LocalDateTime.of(Integer.parseInt(year), Month.JANUARY, 1, 0, 0);
        LocalDateTime givenEndDateTime = LocalDateTime.of(Integer.parseInt(year), Month.DECEMBER, 31, 23, 59);

        if (year.equals("")) {

            startOfGivenYearUTS = 1672570800;
            endOfGivenYearUTS = currentTimeUTS;
        } else {

            startOfGivenYearUTS = Service.convertToUnixTimestamp(givenStartDateTime);
            endOfGivenYearUTS = Service.convertToUnixTimestamp(givenEndDateTime);

        }

        for (Document doc : collection.find(filter)) {
            int timestamp = doc.getInteger("timestamp");

            if (timestamp  < endOfGivenYearUTS && timestamp >= startOfGivenYearUTS) {
                scrobbleCounterForYear += 1;
            }
        }

        String finalYear = year;
        int finalScrobbleCounterForWeek = scrobbleCounterForYear;
        return message.getChannel().flatMap(channel -> channel.createMessage("Your total scrobbles for " + finalYear + ": " + finalScrobbleCounterForWeek));


    }



    static Mono<?> getTopScrobbledDays(Message message, GatewayDiscordClient client) throws JsonProcessingException {



        String username = client.getUserById(Snowflake.of(message.getAuthor().get().getId().asLong()))
                .block()
                .getUsername();

        long userid = message.getAuthor().get().getId().asLong();

        if (!message.getUserMentions().isEmpty()) {
            username = message.getUserMentions().get(0).getUsername();
            userid = message.getUserMentions().get(0).getId().asLong();
        }



        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection(username);
        MongoCollection<Document> users = database.getCollection("users");

        Document userDocument = users.find(Filters.eq("userid", userid)).first();

        Map<String, Integer> topScrobbledDays = new HashMap<>();


        ZoneId userTimeZone = ZoneId.of(userDocument.getString("timezone"));


        for (Document doc : collection.find()) {

            int timestamp = doc.getInteger("timestamp");

            Instant instant = Instant.ofEpochSecond(timestamp);
            ZonedDateTime zonedDateTime = instant.atZone(userTimeZone);
            String firstDate = zonedDateTime.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
            int currentCount = topScrobbledDays.getOrDefault(firstDate, 0);
            topScrobbledDays.put(firstDate, currentCount + 1);


        }


        List<Map.Entry<String, Integer>> topScrobbledDaysList = new ArrayList<>(topScrobbledDays.entrySet());
        Collections.sort(topScrobbledDaysList, Map.Entry.<String, Integer>comparingByValue().reversed());


        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed("Top Scrobbled Days");

        int count = 1;
        StringBuilder artistTracksAll = new StringBuilder();
        for (Map.Entry<String, Integer> entry : topScrobbledDaysList) {
            if (entry.getValue() > 350) {
                continue;
            }
            artistTracksAll.append(count).append(". ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            count++;
            if (count > 10) {
                break;
            }
        }

        embedBuilder.addField("top scrobbled days for " + username, artistTracksAll.toString(), false);
        embedBuilder.footer("Days with more than 350 scrobbles have been ignored", "https://i.imgur.com/F9BhEoz.png");
        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));


    }


    static Mono<?> getDailyListeningTime(Message message, GatewayDiscordClient client) throws JsonProcessingException, UnsupportedEncodingException {


        String[] command = message.getContent().split(" ");


        String timePeriod = "";
        if (command.length >= 2) {
            timePeriod = command[1];
            System.out.println("timePeriod: " + timePeriod);
        } else {
            return message.getChannel().flatMap(channel -> channel.createMessage("Valid commands: $time lifetime $time 2024, $time 2023, $time 2022, $time today, $time currentweek, $time lastweek, $time month etc"));

        }


        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> userCollection = database.getCollection("users");

        String username = client.getUserById(Snowflake.of(message.getAuthor().get().getId().asLong()))
                .block()
                .getUsername();

        long userid = message.getAuthor().get().getId().asLong();

        if (!message.getUserMentions().isEmpty()) {
            username = message.getUserMentions().get(0).getUsername();
            userid = message.getUserMentions().get(0).getId().asLong();
        }

        MongoCollection<Document> scrobblesCollection = database.getCollection(username);

        Document userDocument = userCollection.find(Filters.eq("userid", userid)).first();

        ZoneId userTimeZone = ZoneId.of(userDocument.getString("timezone"));

        long userRegsiteredTimestamp = Long.parseLong(userDocument.getString("registered"));


        int listeningTime;

        //if command is 'today'
        ZonedDateTime currentTimeInTimezone = ZonedDateTime.now(userTimeZone);

        ZonedDateTime firstMinuteOfPeriod;
        ZonedDateTime lastMinuteOfPeriod;

        try {
            List<ZonedDateTime> firstAndLastTimes =  Service.calculateTimePeriod(timePeriod, currentTimeInTimezone, userRegsiteredTimestamp, userTimeZone);

            firstMinuteOfPeriod = firstAndLastTimes.get(0);
            lastMinuteOfPeriod = firstAndLastTimes.get(1);

        } catch (IllegalArgumentException e) {
            return message.getChannel().flatMap(channel -> channel.createMessage("Valid periods: today, currentweek, lastweek, month, 2024, lifetime"));
        }


        System.out.println("This is first minute: " + firstMinuteOfPeriod);
        System.out.println("This is last minute: " +  lastMinuteOfPeriod);

        long utsStartOfPeriod = firstMinuteOfPeriod.toInstant().getEpochSecond();

        long utsEndOfPeriod = lastMinuteOfPeriod.toInstant().getEpochSecond();


        Bson filter = Filters.and(
                Filters.gte("timestamp", utsStartOfPeriod),
                Filters.lt("timestamp", utsEndOfPeriod)
        );

        listeningTime = Service.calculateListeningTime(scrobblesCollection, filter)/60;

        int finalListeningTime = listeningTime;
        String finalTimePeriod = timePeriod;

        return message.getChannel().flatMap(channel -> channel.createMessage("You listened to " + finalListeningTime + " minutes! (" + finalTimePeriod + ")"));
    }


}
