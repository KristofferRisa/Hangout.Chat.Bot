import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpTransport;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;

public class Chatbot {
	
	GoogleCredential credential;
	HttpTransport httpTransport;
	HttpRequestFactory requestFactory;
		
	final String CREDENTIALS_PATH_ENV_PROPERTY = "appconfig.json"; //"GOOGLE_APPLICATION_CREDENTIALS";
	  
	private final String GOOGLE_APPLICATION_CREDENTIALS = "appconfig.json"; // "GOOGLE_APPLICATION_CREDENTIALS";

	// Google Cloud Project ID
	private final String PROJECT_ID = "";
	
	// Cloud Pub/Sub Subscription ID
	private final String SUBSCRIPTION_ID = "";
	
	private final String TOPIC_ID = ""; 
	  
	private final String RESPONSE_URL_TEMPLATE =
		      "https://chat.googleapis.com/v1/__SPACE_ID__/messages";
	  
	private final String HANGOUTS_CHAT_API_SCOPE = "https://www.googleapis.com/auth/chat.bot";
	
	private final String RESPONSE_TEMPLATE = "You said: `__MESSAGE__`";
	
	private final String ADDED_RESPONSE = "Thank you for adding me!";
	
	public Chatbot() throws FileNotFoundException, IOException, GeneralSecurityException {
		credential =
	        GoogleCredential.fromStream(new FileInputStream(GOOGLE_APPLICATION_CREDENTIALS))
        .createScoped(Collections.singleton(HANGOUTS_CHAT_API_SCOPE));
		httpTransport = GoogleNetHttpTransport.newTrustedTransport();
		requestFactory = httpTransport.createRequestFactory(credential);
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(PROJECT_ID, SUBSCRIPTION_ID);
  

	// Instantiate an asynchronous message receiver
	MessageReceiver receiver =
	    new MessageReceiver() {
	      @Override
	      public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
	        // handle incoming message, then ack/nack the received message
	        System.out.println("Id : " + message.getMessageId());
	        System.out.println("Data : " + message.getData().toStringUtf8());
	        System.out.println("Antall att. =" + message.getAttributesCount());
	        
	        ObjectMapper mapper = new ObjectMapper();
	        JsonNode dataJson;
			try {
				dataJson = mapper.readTree(message.getData().toStringUtf8());
				System.out.println("Data : " + dataJson.toString());
		        handle(dataJson);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        
	        
	        consumer.ack();
	      }
	    };
	    ExecutorService pool = Executors.newCachedThreadPool();


	    System.out.println("Starting subscriber...");


		Subscriber subscriber = null;
		try {
		  // Create a subscriber for "my-subscription-id" bound to the message receiver
		  subscriber = Subscriber.newBuilder(subscriptionName, receiver).build();
		  subscriber.addListener(
			        new Subscriber.Listener() {
			          public void failed(Subscriber.State from, Throwable failure) {
			            // Handle error.
			          }
			        },
			        pool);
		  
		  subscriber.startAsync();
		  System.out.println("Press any key to close");
		  
		  in.readLine();
		  
		} finally {
		  // stop receiving messages
		  if (subscriber != null) {
			  
			  subscriber.stopAsync();
		
		      System.out.println("Connection closed");
		      System.exit(1);
		  }
		}
	}

	public void handle(JsonNode eventJson) throws Exception {
		JsonNodeFactory jsonNodeFactory = new JsonNodeFactory(false);
		ObjectNode responseNode = jsonNodeFactory.objectNode();
		
		// Construct the response depending on the event received.
		
		String eventType = eventJson.get("type").asText();
		switch (eventType) {
		  case "ADDED_TO_SPACE":
		    responseNode.put("text", ADDED_RESPONSE);
		    // A bot can also be added to a room by @mentioning it in a message. In that case, we fall
		    // through to the MESSAGE case and let the bot respond. If the bot was added using the
		    // invite flow, we just post a thank you message in the space.
		    if(!eventJson.has("message")) {
		      break;
		    }
		  case "MESSAGE":
		    responseNode.put("text",
		        RESPONSE_TEMPLATE.replaceFirst(
		            "__MESSAGE__", eventJson.get("message").get("text").asText()));
		    // In case of message, post the response in the same thread.
		    ObjectNode threadNode = jsonNodeFactory.objectNode();
		    threadNode.put("name", eventJson.get("message").get("thread").get("name").asText());
		    responseNode.put("thread", threadNode);
		    break;
		  case "REMOVED_FROM_SPACE":
		  default:
		    // Do nothing
		    return;
    }

    // Post the response to Hangouts Chat.

    String URI =
        RESPONSE_URL_TEMPLATE.replaceFirst(
            "__SPACE_ID__", eventJson.get("space").get("name").asText());
    GenericUrl url = new GenericUrl(URI);

    HttpContent content =
        new ByteArrayContent("application/json", responseNode.toString().getBytes("UTF-8"));
    HttpRequest request = requestFactory.buildPostRequest(url, content);
    com.google.api.client.http.HttpResponse response = request.execute();
  }
}
