
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class Consumer {
    private static Scanner in;

    public static void main(String[] argv) throws Exception {
        if (argv.length != 2) {
            System.err.printf("Usage: %s <topicName> <groupId>\n",
                    Consumer.class.getSimpleName());
            System.exit(-1);
        }
        in = new Scanner(System.in);
        String topicName = argv[0];
        String groupId = argv[1];

        ConsumerThread consumerRunnable = new ConsumerThread(topicName, groupId);
        consumerRunnable.start();
        String line = "";
        while (!line.equals("exit")) {
            line = in.next();
        }
        consumerRunnable.getKafkaConsumer().wakeup();
        System.out.println("Stopping consumer .....");
        consumerRunnable.join();
    }

    private static class ConsumerThread extends Thread {
        private String topicName;
        private String groupId;
        private KafkaConsumer<String, String> kafkaConsumer;

        public ConsumerThread(String topicName, String groupId) {
            this.topicName = topicName;
            this.groupId = groupId;
        }

        public void run() {
            Properties configProperties = new Properties();
            configProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
            configProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArrayDeserializer");
            configProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
            configProperties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
            configProperties.put(ConsumerConfig.CLIENT_ID_CONFIG, "id1");

            //Figure out where to start processing messages from
            kafkaConsumer = new KafkaConsumer<String, String>(configProperties);
            kafkaConsumer.subscribe(Arrays.asList(topicName));
            //Start processing messages
            try {
                while (true) {
                    ConsumerRecords<String, String> records = kafkaConsumer.poll(100);
                    for (ConsumerRecord<String, String> record : records) {
                        String tmp = record.value();
                        String[] arrOfStr = tmp.split("\\|");
                        System.out.println(record.value()); //Prints the recorded log

                        //Returns the omitted string with no leading and trailing spaces.
                        int x = 0;
                        for (String a : arrOfStr){
                            arrOfStr[x] = arrOfStr[x].trim();
                            x++;
                        }

                        //Split timeStamp into logDate and logTime to convert into ISO8601 standard
                        //String[] date = arrOfStr[0].split("\\s+");
                        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        isoFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
                        Date logDate = null;
                        try {
                            logDate = isoFormat.parse(arrOfStr[0]);
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
//                        String logDate = arrOfStr[0];


                        String logLevel = arrOfStr[1];
                        String logServer = arrOfStr[2]; //Used as logName and logServer
                        logServer = logServer.replace("[", ""); //Remove brackets
                        logServer = logServer.replace("]", "");

                        String logMain = arrOfStr[3];
                        String logDetail = arrOfStr[4];

                        //Push logs into mongodb
                        MongoClientURI connectionString = new MongoClientURI("mongodb://localhost:27017");
                        MongoClient mongoClient = new MongoClient(connectionString);
                        MongoDatabase database = mongoClient.getDatabase("TEBLogs");
                        MongoCollection<Document> collection = database.getCollection("logs");
                        Document doc = new Document("name", logServer).append("date", logDate)
                                .append("level", logLevel).append("server", logServer).append("detail", logDetail)
                                .append("source", logMain);
                        collection.insertOne(doc);
                    }
                    //"date" : ISODate("2014-01-01T08:15:39.736Z")
                }
            } catch (WakeupException ex) {
                System.out.println("Exception caught " + ex.getMessage());
            }  finally {
                kafkaConsumer.close();
                System.out.println("After closing KafkaConsumer");
            }
        }

        public KafkaConsumer<String, String> getKafkaConsumer() {
            return this.kafkaConsumer;
        }
    }
}