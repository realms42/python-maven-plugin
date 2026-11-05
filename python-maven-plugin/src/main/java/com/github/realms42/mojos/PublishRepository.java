package com.github.realms42.mojos;

// A Python package index to upload to, configured like a <distributionManagement> repository:
// the id names the settings.xml <server> that holds its credentials.
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
