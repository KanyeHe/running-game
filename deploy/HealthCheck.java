import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

/** Uses the JRE already in the runtime image; curl/wget is not required. */
public final class HealthCheck {
    public static void main(String[] args) {
        String address=System.getenv().getOrDefault("HEALTHCHECK_URL","http://127.0.0.1:8080/api/health");
        HttpURLConnection connection=null;
        int result=1;
        try {
            connection=(HttpURLConnection)URI.create(address).toURL().openConnection();
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(5000);
            if(connection.getResponseCode()==200) {
                try(var input=connection.getInputStream()) {
                    String body=new String(input.readAllBytes(),StandardCharsets.UTF_8);
                    if(body.matches("(?s).*\"status\"\\s*:\\s*\"UP\".*"))result=0;
                }
            }
        }catch(Exception unavailable){result=1;}
        finally {if(connection!=null)connection.disconnect();}
        System.exit(result);
    }
}
