package io.github.realms42.mojos;

public class PublishRepository {

    private String id;

    private String url;

    public PublishRepository() {
    }

    PublishRepository(String id, String url) {
        this.id = id;
        this.url = url;
    }

    public String getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }
}
