package photos.example.com.photos;

/**
 * Created by dbhasin on 3/24/17.
 */

public class Photo {

    public String farm;
    public String server;
    public String id;
    public String secret;
    public String url;

    public Photo(String farm, String server, String id, String secret) {
        this.farm = farm;
        this.server = server;
        this.id = id;
        this.secret = secret;

        this.url = "http://farm"+farm+".static.flickr.com/"+server+"/"+id+"_"+secret+".jpg";
    }

    public String getUrl() {

        return this.url;

    }
}
